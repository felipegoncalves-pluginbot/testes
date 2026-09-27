#!/usr/bin/env python3
"""Viewer local para o Firefox: http://127.0.0.1:8765
Usa ADB (não depende do firewall Wi-Fi do Android)."""
from __future__ import print_function

import os
import subprocess
import sys
import threading
import time

try:
    from http.server import BaseHTTPRequestHandler, HTTPServer
except ImportError:
    from BaseHTTPServer import BaseHTTPRequestHandler, HTTPServer

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(ROOT, "out")
SNAP = os.path.join(ROOT, "snapshot.sh")
PORT = int(os.environ.get("SANBOT_VIEWER_PORT", "8765"))

HTML = b"""<!doctype html><meta charset=utf-8><title>Sanbot debug</title>
<style>body{font-family:sans-serif;background:#111;color:#9f9;margin:16px}
img{background:#000;image-rendering:pixelated;max-width:32%;vertical-align:top}
label{display:inline-block;width:32%;color:#888;font-size:12px}
pre{white-space:pre-wrap;font-size:13px;background:#000;padding:8px}</style>
<h1>Sanbot debug (PC via ADB)</h1>
<p><label>HD cam</label><label>Astra+esqueleto</label><label>Tablet</label><br>
<img id=c src=/rgb.jpg width=320 height=240>
<img id=v src=/vision.png width=320 height=240>
<img id=s src=/screen.png width=320></p>
<pre id=j>loading</pre>
<script>
function tick(){
  fetch('/state.json').then(r=>r.text()).then(t=>{
    document.getElementById('j').textContent=t;
    var n=Date.now();
    document.getElementById('s').src='/screen.png?t='+n;
    document.getElementById('v').src='/vision.png?t='+n;
    document.getElementById('c').src='/rgb.jpg?t='+n;
  }).catch(e=>{document.getElementById('j').textContent=String(e)});
  setTimeout(tick, 800);
}
tick();
</script>
"""


def refresh():
    env = os.environ.copy()
    try:
        subprocess.run(
            ["bash", SNAP],
            cwd=os.path.dirname(ROOT),
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=40,
        )
    except Exception:
        pass


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        return

    def send_file(self, name, mime):
        path = os.path.join(OUT, name)
        if not os.path.isfile(path) or os.path.getsize(path) < 8:
            self.send_response(204)
            self.end_headers()
            return
        data = open(path, "rb").read()
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path in ("/", "/index.html"):
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(HTML)))
            self.end_headers()
            self.wfile.write(HTML)
        elif path == "/state.json":
            self.send_file("state.json", "application/json")
        elif path == "/vision.png":
            self.send_file("vision.png", "image/png")
        elif path == "/rgb.jpg" or path == "/camera.jpg":
            self.send_file("rgb.jpg", "image/jpeg")
        elif path == "/screen.png":
            self.send_file("screen.png", "image/png")
        elif path == "/health":
            body = b'{"ok":true,"via":"adb"}'
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()


def loop_refresh():
    while True:
        refresh()
        time.sleep(1.2)


def main():
    os.makedirs(OUT, exist_ok=True)
    threading.Thread(target=loop_refresh, daemon=True).start()
    httpd = HTTPServer(("127.0.0.1", PORT), Handler)
    print("Abra no Firefox: http://127.0.0.1:%d/" % PORT)
    print("(nao use o IP do robo; o Android bloqueia a porta na Wi-Fi)")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
