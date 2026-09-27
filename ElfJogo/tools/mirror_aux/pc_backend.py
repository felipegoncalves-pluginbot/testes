#!/usr/bin/env python3
"""
Cliente auxiliar de referência para o Modo Espelho (protocolo v1).

Uso:
  pip install websockets
  python3 tools/mirror_aux/pc_backend.py <ip-do-sanbot>

Conecta em ws://<host>:8765/mirror, recebe frames e devolve track JSON.
Substitua `build_track_from_frame` por MediaPipe / outro modelo no futuro.
"""

import asyncio
import json
import sys

try:
    import websockets
except ImportError:
    print("Instale: pip install websockets", file=sys.stderr)
    sys.exit(1)

DEFAULT_PORT = 8765


def build_track_from_frame(frame_msg):
    """Placeholder: centro fixo. Troque por inferência real no PC/celular."""
    return {
        "present": True,
        "centroid": [0.5, 0.5],
        "distanceZ": 1500,
        "leftElev": 0.0,
        "rightElev": 0.0,
        "leftRaised": False,
        "rightRaised": False,
        "gesture": "IDLE",
        "joints": {
            "head": [0.5, 0.2, 1500],
            "neck": [0.5, 0.3, 1500],
            "spine": [0.5, 0.5, 1500],
            "leftHand": [0.2, 0.55, 1500],
            "rightHand": [0.8, 0.55, 1500],
        },
    }


async def mirror_aux_session(host):
    uri = "ws://{}:{}/mirror".format(host, DEFAULT_PORT)
    hello = {"v": 1, "type": "hello", "role": "aux", "name": "pc-backend"}
    frames = 0
    async with websockets.connect(uri, ping_interval=None) as ws:
        await ws.send(json.dumps(hello))
        print("Conectado em", uri, flush=True)
        async for raw in ws:
            msg = json.loads(raw)
            msg_type = msg.get("type")
            if msg_type == "frame":
                frames += 1
                if frames == 1 or frames % 30 == 0:
                    print("frames processados:", frames, flush=True)
                track = {
                    "v": 1,
                    "type": "track",
                    "seq": msg.get("seq", 0),
                    "t": msg.get("t", 0),
                    "result": build_track_from_frame(msg),
                }
                await ws.send(json.dumps(track))
            elif msg_type == "ping":
                pong = {"v": 1, "type": "pong", "seq": msg.get("seq", 0)}
                await ws.send(json.dumps(pong))


async def mirror_aux_main(host):
    delay_s = 2
    while True:
        try:
            await mirror_aux_session(host)
            print("Conexão encerrada pelo host. Reconectando...", flush=True)
        except (websockets.exceptions.ConnectionClosed, OSError) as err:
            print("Desconectado ({0}). Reconectando em {1}s...".format(err, delay_s), flush=True)
        await asyncio.sleep(delay_s)
        delay_s = min(delay_s + 1, 10)


def main():
    host = sys.argv[1] if len(sys.argv) > 1 else "192.168.0.100"
    asyncio.run(mirror_aux_main(host))


if __name__ == "__main__":
    main()
