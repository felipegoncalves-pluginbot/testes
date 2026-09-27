---
name: sanbot-debug
description: >
  Captura o que o Sanbot vê (câmera HD, silhueta Astra, esqueleto, IR, gyro,
  motores) sem pedir foto ao usuário. Use quando tracking/esqueleto/motores
  do Elf ou outro Sanbot precisarem de debug visual. Slash: /sanbot-debug
---

# Sanbot debug

Não peça print. No repo do app:

```bash
export SANBOT_SERIAL=192.168.1.100:5555
export SANBOT_HOST=192.168.1.100
bash tools/sanbot_debug/snapshot.sh
```

Leia **nesta ordem**:

1. `tools/sanbot_debug/out/rgb.jpg` — câmera HD (cadeira, perfil, fundo)
2. `tools/sanbot_debug/out/vision.png` — blob Astra + esqueleto
3. `tools/sanbot_debug/out/screen.png` — tablet
4. `tools/sanbot_debug/out/state.json` — `lHand`/`rHand`, `people`, `sensors.chest`, `pirF`, `gyro`

HD e Astra têm FOV diferentes: RGB mostra o objeto que a profundidade pode
estar tratando como pessoa. Protocolo: `tools/sanbot_debug/PROTOCOL.md`.
Pacote Android: `com.sanbot.debug`.
