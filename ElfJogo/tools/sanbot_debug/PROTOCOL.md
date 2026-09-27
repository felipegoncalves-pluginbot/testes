# Sanbot debug kit (reutilizável)

Pacote Android: `com.sanbot.debug` (copie para qualquer app Sanbot).
Host: estes scripts. Agentes: skill `sanbot-debug`.

## No robô (porta 8090)

| GET | Conteúdo |
|-----|----------|
| `/` | viewer HTML (auto-refresh) |
| `/health` | `{"ok":true}` |
| `/state.json` | pose + motores + `sensors` (IR/gyro/PIR) |
| `/vision.png` | 160×120 silhueta Astra + esqueleto |
| `/rgb.jpg` | 160×120 câmera HD (JPEG). Alias `/camera.jpg` |

Arquivos em `cache/sanbot-debug/` (fallback ADB se o HTTP estiver bloqueado).

RAM: 2 bitmaps 160×120 (Astra + RGB) + `int[19200]` + flush ≤2 Hz num executor
próprio. Não copia NV21 640×480. Não roda no loop de tracking.

A HD cam e a Astra **não compartilham o mesmo FOV**. Use RGB para ver cadeira,
pessoa de lado, objeto atrás do blob; use a silhueta para o que o tracker
escolheu.

## Firefox no PC (não use o IP do robô)

O Android OEM do Sanbot recusa conexões inbound na Wi-Fi mesmo com o
servidor em `0.0.0.0:8090`. Abra **no PC**:

```bash
export SANBOT_SERIAL=192.168.1.100:5555
bash tools/sanbot_debug/open_viewer.sh
```

- Viewer (HD + Astra + tela): http://127.0.0.1:8765/
- Hub direto (após `adb forward`): http://127.0.0.1:8090/

Não use `http://192.168.1.100:8090/` — vai falhar com “não foi possível conectar”.

```bash
export SANBOT_SERIAL=192.168.1.100:5555
export SANBOT_HOST=192.168.1.100
./tools/sanbot_debug/snapshot.sh
```

Saída em `tools/sanbot_debug/out/`:

- `rgb.jpg` — câmera HD (sentado de lado, cadeira, fundo)
- `vision.png` — o que a Astra+esqueleto viram
- `screen.png` — tela do tablet (ADB screencap)
- `state.json` — juntas, motores, `sensors.chest` / `gyro` / `pirF`
- `logcat.txt` — últimas linhas úteis

`sensors.ir[]` índice = part SDK 1–17 (`chest` = 13, `torsoL/R` = 11/12).

MCP (opcional): `python3 tools/sanbot_debug/mcp_server.py` — tools `sanbot_snapshot`, `sanbot_state`, `sanbot_logcat`.
