# MIRROR_TRACKING_PROTOCOL v1

Transporte: **WebSocket texto (JSON)** em `ws://<sanbot-ip>:8765/mirror`.

## Papéis

| Papel | Dispositivo | Responsabilidade |
|-------|-------------|------------------|
| **host** | Sanbot (robô) | Captura RGB+depth, envia `frame`, aplica `track` no modo espelho |
| **aux** | Celular/PC | Recebe `frame`, devolve `track` com modelo mais pesado |

O robô **sempre** mantém tracking local (`KinectTrackingEngine`). O auxiliar é upgrade opcional; se cair, o host usa só o local (degradação graciosa).

## Handshake

```json
{"v":1,"type":"hello","role":"aux","name":"pixel-8"}
{"v":1,"type":"hello_ack","role":"host","proto":1}
```

## Heartbeat

```json
{"v":1,"type":"ping","seq":42,"t":1710000000123}
{"v":1,"type":"pong","seq":42}
```

- Host considera aux **vivo** se recebeu `track` ou `pong` nos últimos **300 ms**.
- Aux considera host vivo se recebe `frame` ou `ping` recente.

## Frame (host → aux)

```json
{
  "v": 1,
  "type": "frame",
  "seq": 100,
  "t": 1710000000456,
  "rgbW": 320,
  "rgbH": 240,
  "rgbB64": "<NV21 base64>",
  "depthW": 64,
  "depthH": 48,
  "depthB64": "<short[] little-endian base64>"
}
```

- Taxa alvo: **15 Hz** (host faz stride temporal).
- RGB: NV21 downscaled; depth: mm `uint16` little-endian.

## Track (aux → host)

```json
{
  "v": 1,
  "type": "track",
  "seq": 100,
  "t": 1710000000500,
  "result": { ... TrackingResult v1 ... }
}
```

`seq` deve corresponder ao `frame` processado.

### Fusão no host (`HybridRemoteMerge`)

- Remoto **válido** se: aux conectado, `now - t < 300 ms`, `result.present == true`.
- Remoto válido → **esqueleto completo** (cabeça, tronco, braços, pernas), gestos e overlay usam o aux.
- `present == false` ou track expirado → host usa só tracking local (degradação graciosa).
- Diagnóstico local (FPS, contagem depth) permanece no `TrackingResult.diagnostics`.

## TrackingResult v1 (campos)

| Campo | Tipo | Notas |
|-------|------|-------|
| `present` | bool | `isPlayerPresent` |
| `centroid` | [x,y] | normalizado 0..1 |
| `distanceZ` | int | mm |
| `leftElev` / `rightElev` | float | 0..1 |
| `leftRaised` / `rightRaised` | bool | |
| `gesture` | string | enum `KinectTrackingEngine.GestureType` |
| `joints` | object | chaves: `head`, `neck`, … → `[x,y,z]` |

Coordenadas seguem `docs/specs/COORDINATE_FRAMES.md` (sem `1.0f - x`).

## Referência PC

`tools/mirror_aux/pc_backend.py` — cliente auxiliar de referência (eco/pass-through para testes).

## App Android (produção no evento)

Módulo Gradle `:mirror-aux` (`com.felipe.elfmirror`):

- APK para o celular do operador — **sem PC nem Python no evento**.
- Conecta em `ws://<ip-sanbot>:8765/mirror`, envia `hello`, processa `frame` com **ML Kit Pose Accurate**, devolve `track`.
- Build: `./gradlew :mirror-aux:assembleDebug`
- Instalar: `adb install -r mirror-aux/build/outputs/apk/debug/mirror-aux-debug.apk`

No robô: abrir **Modo Espelho** (relay sobe automaticamente via `MirrorSessionSetup`).
No celular: informar o IP do Sanbot na mesma rede Wi‑Fi e tocar **Conectar**.
