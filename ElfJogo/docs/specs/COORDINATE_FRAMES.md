# Contrato de eixos (fonte da verdade)

Qualquer prompt de tracking/cinemática lê **este** arquivo. Specs em prosa, `.mdc` e código
que divergirem deste contrato estão errados. Os testes em
`CoordinateContractTest`, `HeadKinematicsGuardTest`, `HeadGazeServoTest` e
`RobotMountingTrackingTest` **são** a spec executável.

## 0. Montagem física (confirmada no robô)

- O Sanbot Elf mede ~0,90 m. **O Astra e a câmera HD ficam os dois na cabeça.** Versões antigas
  deste arquivo diziam "Astra no peito" e estavam erradas.
- Altura do Astra: ~0,8 m (fallback `GroundPlane.DEFAULT_CAMERA_HEIGHT_M`). A altura e o pitch reais
  são medidos pelo piso (`GroundPlaneEstimator`, faixa aceita de 0,45 a 1,10 m).
- Consequências:
  - **Girar a cabeça move a câmera.** Todo controle de cabeça a partir da imagem é malha fechada:
    ver §3.
  - **Yaw não muda o plano do chão** (giro em torno do eixo vertical); **pitch muda**. Inclinar a
    cabeça 6° para baixo tira o jogador por ~4 reestimativas do plano (~120 frames) no sintético.
  - Com o sensor a ~0,8 m e a cabeça nivelada, a cabeça de um adulto só entra no quadro a partir de
    ~2,3–2,5 m. Mais perto, o tronco é rastreado sem a cabeça (`PersonGate.MAX_TORSO_BAND_HEAD_CUT_M`).

## 1. Depth Astra (óptico)

| Eixo | Faixa | Significado |
|------|-------|-------------|
| X | `[0, 1]` | 0 = esquerda do sensor, 1 = direita do sensor |
| Y | `[0, 1]` | 0 = topo do quadro, 1 = chão |
| Z | mm | motor métrico: `[450, 4200]`; ver §0 para a distância de jogo |

## 2. MoveNet RGB

- `opticalXFromMoveNet(modelX) == modelX`. **Proibido** `1.0f - x` no decoder.
- Usuário de frente: `RIGHT_SHOULDER` anatômico cai no **screen-left** (X menor).
- `PoseDepthFusion` mapeia `RIGHT_*` → juntas `left*` de tela. Esse swap é o único
  espelho. Inverter X **e** trocar L/R é espelho duplo.

## 3. Cabeça do Sanbot

Sinal (direção em que a cabeça gira para olhar o jogador):

| Sensor X | passo de `targetHeadYaw` | `hardwareYaw` |
|----------|--------------------------|---------------|
| `X < 0.5` (esquerda da câmera / direita do usuário) | **positivo** | **> 90°** |
| `X > 0.5` (direita da câmera / esquerda do usuário) | **negativo** | **< 90°** |

Centro hardware: yaw 90°, pitch 18°. Faixa segura yaw `[30, 150]`, pitch `[10, 28]`.
`hardwareYaw = 90 + yawOffset`.

Como a câmera está na cabeça, **o X não é mapeado para yaw absoluto**. O mapa antigo
`(0,5 − x)·60` oscilava sem parar nos jogos (ganho de malha ~1 com atraso de visão) e, com EMA
no espelho, deixava o jogador a ~20% do centro. O controle é o `HeadGazeServo`:

- zona central de ±0,12 do quadro: a cabeça fica parada, como a câmera do Kinect;
- fora dela, um passo de `0,7 × erro × 58,4°` (no máximo 20°) sobre o yaw já comandado;
- o próximo passo só depois de 600 ms, para a cabeça assentar e a visão enxergar o resultado.

Usado por `RobotGameFeedback.trackTargetX` (jogos) e pelo `MirrorGameEngine` com o jogador em pé.
Sentado, o espelho imita o giro da cabeça do usuário relativo ao tronco (invariante ao giro da
câmera, ganho absoluto 0,35: estável).

**Pitch não é comandado a partir da imagem** (fica no nível, 18°): inclinar a cabeça desloca o
plano do chão (§0).

## 4. Asas

- Espelho frontal: lado esquerdo da câmera → asa esquerda do robô
  (`result.leftHandElevation` → `targetLeftWingAngle`).
- Proporcional: `setMirrorWingAngles`, não `setMirrorWings`.
- `MIRROR_UPDATE_MIN_MS = 150` (taxa do ângulo). Debounce binário UP/DOWN = 350 ms.
- Um smoother só (`ArmElevationTracker`). Sem segundo EMA em `MirrorGameEngine`.

## 5. O que a IA não pode fazer sem evidência de device

- Tunar Holt / EMA / Schmitt.
- Adicionar `1.0f - x` ou mais um swap L/R.
- Empilhar outro smoother (já existem EMA + Holt).
- Trocar o sinal do yaw “porque a spec antiga dizia o contrário”.
- Voltar a mapear X da imagem para yaw/pitch absoluto da cabeça (§3).

## 6. Overlay espelho (Astra 4:3 vs HD 16:9)

- Juntas vivem no depth space (Kinect BodyBasics). HD e Astra estão **os dois na cabeça** e giram
  juntos: **não existe pan pelo yaw da cabeça** entre imagem e esqueleto (`MirrorDisplayGuardTest`).
- Fullscreen: `letterboxDest` 4:3.
- Proibido esticar 4:3 no 16:9 (`centerCrop` no overlay fullscreen) — desloca X/Y.
