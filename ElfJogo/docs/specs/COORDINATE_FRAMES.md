# Contrato de eixos (fonte da verdade)

Qualquer prompt de tracking/cinemática lê **este** arquivo. Specs em prosa, `.mdc` e código
que divergirem deste contrato estão errados. Os testes em
`CoordinateContractTest` e `HeadKinematicsGuardTest` **são** a spec executável.

## 1. Depth Astra (óptico)

| Eixo | Faixa | Significado |
|------|-------|-------------|
| X | `[0, 1]` | 0 = esquerda do sensor, 1 = direita do sensor |
| Y | `[0, 1]` | 0 = topo do quadro, 1 = chão |
| Z | mm | `[500, 2200]`, jogo em `1400–1700` |

## 2. MoveNet RGB

- `opticalXFromMoveNet(modelX) == modelX`. **Proibido** `1.0f - x` no decoder.
- Usuário de frente: `RIGHT_SHOULDER` anatômico cai no **screen-left** (X menor).
- `PoseDepthFusion` mapeia `RIGHT_*` → juntas `left*` de tela. Esse swap é o único
  espelho. Inverter X **e** trocar L/R é espelho duplo.

## 3. Cabeça do Sanbot

| Sensor X | `targetHeadYaw` | `hardwareYaw` |
|----------|-----------------|---------------|
| `X < 0.5` (esquerda da câmera / direita do usuário) | **positivo** | **> 90°** |
| `X > 0.5` (direita da câmera / esquerda do usuário) | **negativo** | **< 90°** |

Centro hardware: yaw 90°, pitch 18°. Faixa segura yaw `[30, 150]`, pitch `[10, 28]`.
Fórmula: `hardwareYaw = 90 + yawOffset`. Código: `MirrorGameEngine` usa
`(deltaX) * -60` depois da deadzone.

## 4. Asas

- Espelho frontal: lado esquerdo da câmera → asa esquerda do robô
  (`result.leftHandElevation` → `targetLeftWingAngle`).
- Proporcional: `setMirrorWingAngles`, não `setMirrorWings`.
- `MIRROR_UPDATE_MIN_MS = 150` (taxa do ângulo). Debounce binário UP/DOWN = 350 ms.
- Um smoother só (`ArmElevationTracker`). Sem segundo EMA em `MirrorGameEngine`.

## 6. Overlay espelho (Astra 4:3 vs HD 16:9)

- Juntas vivem no depth space (Kinect BodyBasics). HD está na cabeça; Astra no peito.
- Fullscreen: `letterboxDest` 4:3 + `panNormFromHeadYaw` (ganho 60, o mesmo do yaw).
- Proibido esticar 4:3 no 16:9 (`centerCrop` no overlay fullscreen) — desloca X/Y.

## 5. O que a IA não pode fazer sem evidência de device

- Tunar Holt / EMA / Schmitt.
- Adicionar `1.0f - x` ou mais um swap L/R.
- Empilhar outro smoother (já existem EMA + Holt).
- Trocar o sinal do yaw “porque a spec antiga dizia o contrário”.
