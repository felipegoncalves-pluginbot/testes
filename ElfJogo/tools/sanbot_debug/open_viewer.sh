#!/usr/bin/env bash
# Firefox no PC: http://127.0.0.1:8765  (ADB, ignora firewall do Android)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export SANBOT_SERIAL="${SANBOT_SERIAL:-192.168.1.100:5555}"
export SANBOT_HOST="${SANBOT_HOST:-192.168.1.100}"
adb connect "$SANBOT_SERIAL" >/dev/null 2>&1 || true
adb -s "$SANBOT_SERIAL" forward tcp:8090 tcp:8090 >/dev/null 2>&1 || true
echo "Nao use o IP do robo no Firefox (firewall Android)."
echo "Hub via ADB:  http://127.0.0.1:8090/"
echo "Viewer (tela+visao): http://127.0.0.1:8765/"
exec python3 "$ROOT/viewer.py"
