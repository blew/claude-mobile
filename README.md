# Blew's Claude Bridge

<p align="center">
  <img src="icon.png" width="180" alt="Blew's Claude Bridge icon" />
</p>

<p align="center">
  A mobile bridge that tunnels Claude Code through VS Code Remote Tunnel — coding from your phone, powered by Claude.
</p>

---

## What it does

- **`proxy/`** — Python proxy that sits between the mobile app and the Claude API
- **`android/`** — Native Android app (Kotlin + Compose) for chatting with Claude on the go

## Setup

1. Run the proxy: `python proxy/claude_proxy.py`
2. Open a VS Code Remote Tunnel to your machine
3. Install and launch the Android app
4. Point the app at your proxy endpoint
