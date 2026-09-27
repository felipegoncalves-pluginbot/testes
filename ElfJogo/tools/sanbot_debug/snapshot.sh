#!/usr/bin/env bash
# Snapshot para agentes: tela + visão + estado + logcat. Sem pedir foto ao usuário.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${SANBOT_OUT:-$ROOT/tools/sanbot_debug/out}"
SERIAL="${SANBOT_SERIAL:-192.168.1.100:5555}"
HOST="${SANBOT_HOST:-192.168.1.100}"
PORT="${SANBOT_DEBUG_PORT:-8090}"
PKG="${SANBOT_PKG:-com.felipe.elftemplate}"
mkdir -p "$OUT"

adb connect "$SERIAL" >/dev/null 2>&1 || true
adb -s "$SERIAL" forward tcp:"$PORT" tcp:"$PORT" >/dev/null 2>&1 || true

echo "== screencap =="
if ! adb -s "$SERIAL" exec-out screencap -p > "$OUT/screen.png" 2>/dev/null; then
  echo "screencap falhou" >&2
  rm -f "$OUT/screen.png"
fi
# PNG via exec-out às vezes vem com \r no header no Android 6
python3 - "$OUT/screen.png" <<'PY' || true
import sys
p = sys.argv[1]
try:
    with open(p, "rb") as f:
        b = f.read()
except Exception:
    sys.exit(0)
i = b.find(b"\x89PNG")
if i > 0:
    open(p, "wb").write(b[i:])
PY

echo "== HTTP state/vision =="
# 1) localhost via adb forward (firewall OEM bloqueia 192.168.x:8090)
# 2) IP do robô  3) cache run-as
got_http=0
for url in "http://127.0.0.1:$PORT" "http://$HOST:$PORT"; do
  if curl -sS --connect-timeout 1 --max-time 3 "$url/state.json" -o "$OUT/state.json"; then
    curl -sS --connect-timeout 1 --max-time 3 "$url/vision.png" -o "$OUT/vision.png" || true
    curl -sS --connect-timeout 1 --max-time 3 "$url/rgb.jpg" -o "$OUT/rgb.jpg" || true
    echo "HTTP ok $url"
    got_http=1
    break
  fi
done
if [ "$got_http" -eq 0 ]; then
  echo "HTTP 8090 indisponível; tentando cache via adb"
  adb -s "$SERIAL" shell "run-as $PKG cat cache/sanbot-debug/state.json" > "$OUT/state.json" 2>/dev/null || echo "{}" > "$OUT/state.json"
  adb -s "$SERIAL" shell "run-as $PKG cat cache/sanbot-debug/vision.png" > "$OUT/vision.png" 2>/dev/null || true
  adb -s "$SERIAL" shell "run-as $PKG cat cache/sanbot-debug/rgb.jpg" > "$OUT/rgb.jpg" 2>/dev/null || true
fi

echo "== logcat =="
adb -s "$SERIAL" logcat -d -t 80 -v time \
  -s AstraDepth:I MirrorGameEngine:I TFLitePoseProcessor:I SanbotDebug:I \
     VisionMediaDecoder:W art:I AndroidRuntime:E DBG938fa7:I \
  > "$OUT/logcat.txt" 2>/dev/null || true

python3 - "$OUT" <<'PY'
import json, os, sys
out = sys.argv[1]
print("out=", out)
for n in ("screen.png", "vision.png", "rgb.jpg", "state.json", "logcat.txt"):
    p = os.path.join(out, n)
    sz = os.path.getsize(p) if os.path.isfile(p) else 0
    print(" ", n, sz, "bytes")
sp = os.path.join(out, "state.json")
try:
    st = json.load(open(sp))
    sen = st.get("sensors") or {}
    print(" player=%s fused=%s z=%s people=%s lHand=%s rHand=%s yawCmd=%s Lwing=%s Rwing=%s mode=%s" % (
        st.get("player"), st.get("fused"), st.get("zMm"), st.get("people"),
        st.get("lHand"), st.get("rHand"),
        (st.get("motors") or {}).get("yawCmd"),
        (st.get("motors") or {}).get("leftWing"),
        (st.get("motors") or {}).get("rightWing"),
        st.get("mode")))
    print(" chestIr=%s pirF=%s gyro=%s rgbMs=%s seated=%s aspect=%s" % (
        sen.get("chest"), sen.get("pirF"), sen.get("gyro"), sen.get("rgbMs"),
        st.get("seated"), st.get("aspect")))
except Exception as e:
    print(" state.json parse:", e)
PY
echo "OK $OUT"
