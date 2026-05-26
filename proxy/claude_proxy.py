#!/usr/bin/env python3
"""
Claude Mobile Proxy Server.

Bridges the Android app (Blew's Claude Bridge) to the Claude Code CLI running
in the AI Workspaces directory. Supports:
  - Streaming output (stream-json + partial messages forwarded as SSE chunks)
  - Multiple concurrent chat tabs via per-tab session_id
  - Listing past Claude Code sessions in the workspace project
  - Fetching messages of a past session so the app can resume it

Endpoints:
  POST /message               Body: {"text": str, "session_id": str|null}
                              Returns SSE with events:
                                {"type": "text", "content": str}      # streaming text
                                {"type": "session", "id": str}        # session_id (sent once)
                                {"type": "error", "content": str}
                                {"type": "done"}
  GET  /sessions              List past sessions in the workspace project.
  GET  /sessions/<id>         Return message list for a specific session.
"""
import atexit
import http.server
import json
import os
import pathlib
import re
import secrets
import signal
import subprocess
import sys
import threading
import time
from urllib.parse import urlparse

PORT = int(os.environ.get("CLAUDE_PROXY_PORT", 8765))
_VERSION = "2.1"
_START_TIME = time.time()
WORKSPACE = r"C:\pascal\AI workspaces"

# subprocess on Windows doesn't inherit the full user PATH from background hosts,
# so npm-installed binaries aren't found. Hardcode and patch into env.
_CLAUDE = r"C:\Users\pasca\AppData\Roaming\npm\claude.cmd"
_NPM_BIN = r"C:\Users\pasca\AppData\Roaming\npm"
_ENV = os.environ.copy()
if _NPM_BIN not in _ENV.get("PATH", ""):
    _ENV["PATH"] = _NPM_BIN + ";" + _ENV.get("PATH", "")

# Project dir Claude uses to store session JSONL files for this workspace.
_PROJECTS_DIR = pathlib.Path.home() / ".claude" / "projects" / "c--pascal-AI-workspaces"

KEY_FILE = pathlib.Path(__file__).parent / ".api_key"
if KEY_FILE.exists():
    API_KEY = KEY_FILE.read_text().strip()
else:
    API_KEY = secrets.token_hex(16)
    KEY_FILE.write_text(API_KEY)
    print(f"[setup] Generated API key: {API_KEY}")

_claude_lock = threading.Lock()


def _build_cmd(text: str, session_id: str | None) -> list[str]:
    parts = [_CLAUDE]
    if session_id:
        parts += ["--resume", session_id]
    parts += [
        "--print",
        "-p",
        text,
        "--output-format",
        "stream-json",
        "--include-partial-messages",
        "--verbose",
        "--dangerously-skip-permissions",
    ]
    return parts


def stream_claude(text: str, session_id: str | None):
    """Generator yielding SSE event dicts for a single user turn."""
    parts = _build_cmd(text, session_id)
    cmd = subprocess.list2cmdline(parts)

    # Serialize subprocess invocations so concurrent tabs don't trample each other's
    # stdin/stdout. Per-tab session_ids let them coexist on disk, but two concurrent
    # claude processes in the same workspace can cause file-lock contention.
    with _claude_lock:
        proc = subprocess.Popen(
            cmd,
            cwd=WORKSPACE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            bufsize=1,
            env=_ENV,
            shell=True,
        )

        emitted_session = False
        try:
            for line in proc.stdout:
                line = line.strip()
                if not line:
                    continue
                try:
                    event = json.loads(line)
                except json.JSONDecodeError:
                    continue

                event_type = event.get("type")

                if event_type == "system" and event.get("subtype") == "init":
                    sid = event.get("session_id")
                    if sid and not emitted_session:
                        yield {"type": "session", "id": sid}
                        emitted_session = True

                elif event_type == "stream_event":
                    inner = event.get("event", {})
                    if inner.get("type") == "content_block_delta":
                        delta = inner.get("delta", {})
                        if delta.get("type") == "text_delta":
                            chunk = delta.get("text", "")
                            if chunk:
                                yield {"type": "text", "content": chunk}

                elif event_type == "result":
                    if event.get("is_error"):
                        yield {"type": "error", "content": event.get("error", "Claude returned an error")}

        finally:
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()


def _extract_session_title(jsonl_path: pathlib.Path) -> str:
    """
    Pull the cleanest available title for a session.

    Claude Code writes an `{"type":"ai-title","aiTitle":"..."}` line once a
    session is established — that's the same title the VS Code extension shows
    in its history panel. Prefer that. Fall back to the first user message
    text for sessions that don't have one yet.
    """
    ai_title = ""
    first_user_text = ""
    try:
        with jsonl_path.open("r", encoding="utf-8") as f:
            for line in f:
                try:
                    entry = json.loads(line)
                except json.JSONDecodeError:
                    continue
                kind = entry.get("type")
                if kind == "ai-title":
                    ai_title = entry.get("aiTitle", "").strip()
                    if ai_title:
                        return ai_title
                elif kind == "user" and not first_user_text:
                    message = entry.get("message", {})
                    content = message.get("content")
                    if isinstance(content, str):
                        first_user_text = content
                    elif isinstance(content, list):
                        for block in content:
                            if isinstance(block, dict) and block.get("type") == "text":
                                first_user_text = block.get("text", "")
                                break
    except OSError:
        pass
    return ai_title or first_user_text


def _count_turns(jsonl_path: pathlib.Path) -> int:
    n = 0
    try:
        with jsonl_path.open("r", encoding="utf-8") as f:
            for line in f:
                try:
                    entry = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if entry.get("type") in ("user", "assistant"):
                    n += 1
    except OSError:
        pass
    return n


def list_sessions() -> list[dict]:
    if not _PROJECTS_DIR.exists():
        return []
    sessions = []
    for jsonl in _PROJECTS_DIR.glob("*.jsonl"):
        sid = jsonl.stem
        title = _extract_session_title(jsonl).strip()
        if not title:
            title = "(empty session)"
        # Trim to a UI-friendly length
        title = title.replace("\n", " ").strip()
        if len(title) > 80:
            title = title[:77] + "…"
        sessions.append({
            "id": sid,
            "title": title,
            "modified": int(jsonl.stat().st_mtime),
            "turns": _count_turns(jsonl),
        })
    sessions.sort(key=lambda s: s["modified"], reverse=True)
    return sessions


def get_session_messages(session_id: str) -> list[dict]:
    """Return [{role, content}] in chronological order for a session, or []."""
    path = _PROJECTS_DIR / f"{session_id}.jsonl"
    if not path.exists():
        return []
    messages: list[dict] = []
    try:
        with path.open("r", encoding="utf-8") as f:
            for line in f:
                try:
                    entry = json.loads(line)
                except json.JSONDecodeError:
                    continue
                kind = entry.get("type")
                if kind not in ("user", "assistant"):
                    continue
                msg = entry.get("message", {})
                role = msg.get("role", kind)
                content = msg.get("content")
                text = ""
                if isinstance(content, str):
                    text = content
                elif isinstance(content, list):
                    for block in content:
                        if isinstance(block, dict) and block.get("type") == "text":
                            text += block.get("text", "")
                if text.strip():
                    messages.append({"role": role, "content": text})
    except OSError:
        pass
    return messages


class ProxyHandler(http.server.BaseHTTPRequestHandler):

    server_version = "ClaudeBridgeProxy/2.0"

    def _authorized(self) -> bool:
        return self.headers.get("X-API-Key", "") == API_KEY

    def _cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-API-Key")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")

    def _send_json(self, code: int, body):
        payload = json.dumps(body).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self._cors_headers()
        self.end_headers()
        self.wfile.write(payload)

    def do_OPTIONS(self):
        self.send_response(200)
        self._cors_headers()
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path

        # /health is unauthenticated — used by the app for connectivity checks.
        if path == "/health":
            self._send_json(200, {
                "status": "ok",
                "version": _VERSION,
                "uptime_s": int(time.time() - _START_TIME),
                "port": PORT,
                "workspace": WORKSPACE,
            })
            return

        if not self._authorized():
            self._send_json(401, {"error": "Unauthorized"})
            return

        if path == "/sessions":
            self._send_json(200, list_sessions())
            return

        m = re.fullmatch(r"/sessions/([A-Za-z0-9_-]+)", path)
        if m:
            self._send_json(200, {"id": m.group(1), "messages": get_session_messages(m.group(1))})
            return

        self._send_json(404, {"error": "Not found"})

    def do_POST(self):
        if not self._authorized():
            self._send_json(401, {"error": "Unauthorized"})
            return

        if self.path != "/message":
            self._send_json(404, {"error": "Not found"})
            return

        length = int(self.headers.get("Content-Length", 0))
        try:
            body = json.loads(self.rfile.read(length))
        except json.JSONDecodeError:
            self._send_json(400, {"error": "Invalid JSON"})
            return

        text = (body.get("text") or "").strip()
        session_id = body.get("session_id") or None

        if not text:
            self._send_json(400, {"error": "Empty message"})
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("X-Accel-Buffering", "no")
        self._cors_headers()
        self.end_headers()

        def sse(event: dict):
            self.wfile.write(f"data: {json.dumps(event)}\n\n".encode("utf-8"))
            self.wfile.flush()

        try:
            for event in stream_claude(text, session_id):
                sse(event)
        except Exception as exc:
            sse({"type": "error", "content": str(exc)})
        finally:
            sse({"type": "done"})

    def log_message(self, fmt, *args):
        sys.stdout.write(f"[{self.log_date_time_string()}] {fmt % args}\n")
        sys.stdout.flush()


class ThreadingHTTPServer(http.server.ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def _log_shutdown(reason: str) -> None:
    uptime = int(time.time() - _START_TIME)
    print(f"\n[{time.strftime('%Y-%m-%d %H:%M:%S')}] Proxy stopping — reason: {reason}, uptime: {uptime}s")
    sys.stdout.flush()


def main():
    ts = time.strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{ts}] Claude Bridge Proxy v{_VERSION} starting")
    print(f"  Workspace  : {WORKSPACE}")
    print(f"  Port       : {PORT}")
    print(f"  API Key    : {API_KEY}")
    print(f"  Projects   : {_PROJECTS_DIR}")
    print(f"  PID        : {os.getpid()}")
    print()
    sys.stdout.flush()

    atexit.register(_log_shutdown, "atexit")
    for sig in (signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, lambda s, f: (_log_shutdown(signal.Signals(s).name), sys.exit(0)))

    server = ThreadingHTTPServer(("", PORT), ProxyHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        _log_shutdown("KeyboardInterrupt")


if __name__ == "__main__":
    main()
