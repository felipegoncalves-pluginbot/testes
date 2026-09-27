#!/usr/bin/env python3
"""MCP stdio mínimo: snapshot / state / logcat do Sanbot (sem dependências)."""
from __future__ import print_function

import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
SNAP = os.path.join(ROOT, "snapshot.sh")
OUT = os.path.join(ROOT, "out")


def send(msg):
    body = json.dumps(msg, separators=(",", ":")).encode("utf-8")
    sys.stdout.buffer.write(("Content-Length: %d\r\n\r\n" % len(body)).encode("ascii"))
    sys.stdout.buffer.write(body)
    sys.stdout.flush()


def read_msg():
    headers = {}
    while True:
        line = sys.stdin.buffer.readline()
        if not line:
            return None
        if line in (b"\r\n", b"\n"):
            break
        decoded = line.decode("utf-8", "replace")
        if ":" not in decoded:
            continue
        key, val = decoded.split(":", 1)
        headers[key.strip().lower()] = val.strip()
    n = int(headers.get("content-length", "0") or "0")
    if n <= 0:
        return None
    raw = sys.stdin.buffer.read(n)
    return json.loads(raw.decode("utf-8"))


def tool_result(call_id, text):
    return {
        "jsonrpc": "2.0",
        "id": call_id,
        "result": {"content": [{"type": "text", "text": text}]},
    }


def run_snapshot():
    env = os.environ.copy()
    proc = subprocess.run(
        ["bash", SNAP],
        cwd=os.path.dirname(ROOT),
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=45,
    )
    out = proc.stdout.decode("utf-8", "replace")
    state_path = os.path.join(OUT, "state.json")
    extra = ""
    if os.path.isfile(state_path):
        extra = "\nstate.json:\n" + open(state_path, "r").read()[:4000]
    return (
        "exit=%s\n%s\nArquivos para o agente ler:\n"
        "  %s/rgb.jpg (câmera HD)\n"
        "  %s/vision.png (Astra+esqueleto)\n"
        "  %s/screen.png (tela do tablet)\n"
        "  %s/state.json\n"
        "  %s/logcat.txt\n%s"
        % (proc.returncode, out, OUT, OUT, OUT, OUT, OUT, extra)
    )


TOOLS = [
    {
        "name": "sanbot_snapshot",
        "description": "Captura câmera HD, silhueta Astra, tela, sensores IR/gyro e logcat do Sanbot via ADB/HTTP.",
        "inputSchema": {"type": "object", "properties": {}},
    },
    {
        "name": "sanbot_logcat",
        "description": "Últimas linhas de logcat do app Sanbot.",
        "inputSchema": {"type": "object", "properties": {}},
    },
]


def handle(msg):
    if not msg:
        return
    mid = msg.get("id")
    method = msg.get("method")
    if method == "initialize":
        send(
            {
                "jsonrpc": "2.0",
                "id": mid,
                "result": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {"tools": {}},
                    "serverInfo": {"name": "sanbot-debug", "version": "1.0.0"},
                },
            }
        )
    elif method == "notifications/initialized":
        return
    elif method == "tools/list":
        send({"jsonrpc": "2.0", "id": mid, "result": {"tools": TOOLS}})
    elif method == "tools/call":
        name = (msg.get("params") or {}).get("name")
        if name == "sanbot_snapshot":
            send(tool_result(mid, run_snapshot()))
        elif name == "sanbot_logcat":
            serial = os.environ.get("SANBOT_SERIAL", "192.168.1.100:5555")
            proc = subprocess.run(
                [
                    "adb",
                    "-s",
                    serial,
                    "logcat",
                    "-d",
                    "-t",
                    "60",
                    "-v",
                    "time",
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                timeout=20,
            )
            send(tool_result(mid, proc.stdout.decode("utf-8", "replace")[-8000:]))
        else:
            send(
                {
                    "jsonrpc": "2.0",
                    "id": mid,
                    "error": {"code": -32601, "message": "unknown tool"},
                }
            )
    elif method == "ping":
        send({"jsonrpc": "2.0", "id": mid, "result": {}})


def main():
    while True:
        msg = read_msg()
        if msg is None:
            break
        handle(msg)


if __name__ == "__main__":
    main()
