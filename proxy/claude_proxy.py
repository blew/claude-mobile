#!/usr/bin/env python3
"""
Claude Mobile Proxy Server
Bridges the Android app to the Claude Code CLI running in the AI Workspaces directory.

Usage: python claude_proxy.py
       Set CLAUDE_PROXY_PORT env var to change port (default: 8765)
"""
import http.server
import json
import os
import pathlib
import secrets
import subprocess
import sys
import threading

PORT = int(os.environ.get("CLAUDE_PROXY_PORT", 8765))
WORKSPACE = r"C:\pascal\AI workspaces"

# subprocess doesn't inherit the full user PATH on Windows, so npm-installed
# binaries aren't found. Hardcode the known location and patch it into env.
_CLAUDE = r"C:\Users\pasca\AppData\Roaming\npm\claude.cmd"
_NPM_BIN = r"C:\Users\pasca\AppData\Roaming\npm"
_ENV = os.environ.copy()
if _NPM_BIN not in _ENV.get("PATH", ""):
    _ENV["PATH"] = _NPM_BIN + ";" + _ENV.get("PATH", "")

# API key: auto-generated on first run, saved to .api_key (gitignored)
KEY_FILE = pathlib.Path(__file__).parent / ".api_key"
if KEY_FILE.exists():
    API_KEY = KEY_FILE.read_text().strip()
else:
    API_KEY = secrets.token_hex(16)
    KEY_FILE.write_text(API_KEY)
    print(f"[setup] Generated API key: {API_KEY}")
    print(f"[setup] Add this as the API_KEY secret in your GitHub repo settings.")

_session_lock = threading.Lock()
_session_active = False


def run_claude(text: str, new_session: bool) -> str:
    global _session_active
    with _session_lock:
        if new_session or not _session_active:
            parts = [_CLAUDE, "--print", "-p", text,
                     "--output-format", "json",
                     "--dangerously-skip-permissions"]
            _session_active = True
        else:
            parts = [_CLAUDE, "--continue", "--print", "-p", text,
                     "--output-format", "json",
                     "--dangerously-skip-permissions"]

    # shell=True routes through cmd.exe, which is required to run .cmd files
    # on Windows. list2cmdline handles proper quoting of all arguments.
    cmd = subprocess.list2cmdline(parts)
    result = subprocess.run(
        cmd,
        cwd=WORKSPACE,
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=300,
        env=_ENV,
        shell=True,
    )

    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"claude exited with code {result.returncode}")

    try:
        data = json.loads(result.stdout)
        return data.get("result", result.stdout.strip())
    except json.JSONDecodeError:
        return result.stdout.strip()


class ProxyHandler(http.server.BaseHTTPRequestHandler):

    def _authorized(self) -> bool:
        return self.headers.get("X-API-Key", "") == API_KEY

    def _cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, X-API-Key")
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")

    def _send_json(self, code: int, body: dict):
        payload = json.dumps(body).encode()
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

    def do_POST(self):
        if self.path != "/message":
            self._send_json(404, {"error": "Not found"})
            return

        if not self._authorized():
            self._send_json(401, {"error": "Unauthorized"})
            return

        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length))
        text = body.get("text", "").strip()
        new_session = bool(body.get("new_session", False))

        if not text:
            self._send_json(400, {"error": "Empty message"})
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self._cors_headers()
        self.end_headers()

        def sse(event: dict):
            line = f"data: {json.dumps(event)}\n\n"
            self.wfile.write(line.encode("utf-8"))
            self.wfile.flush()

        try:
            response = run_claude(text, new_session)
            sse({"type": "text", "content": response})
        except Exception as exc:
            sse({"type": "error", "content": str(exc)})
        finally:
            sse({"type": "done"})

    def log_message(self, fmt, *args):
        sys.stdout.write(f"[{self.log_date_time_string()}] {fmt % args}\n")
        sys.stdout.flush()


def main():
    print(f"Claude Mobile Proxy")
    print(f"  Workspace : {WORKSPACE}")
    print(f"  Port      : {PORT}")
    print(f"  API Key   : {API_KEY}")
    print()
    server = http.server.HTTPServer(("", PORT), ProxyHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")


if __name__ == "__main__":
    main()
