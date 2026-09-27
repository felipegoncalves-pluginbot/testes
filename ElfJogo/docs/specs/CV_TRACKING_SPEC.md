# ESPECIFICAÇÃO DO PIPELINE DE VISÃO COMPUTACIONAL & TRACKING 3D

## 1. Sistema de Coordenadas e Normalização
- **Eixo X**: Normalizado `[0.0, 1.0]` (0.0 = Extrema Esquerda da Câmera, 1.0 = Extrema Direita).
- **Eixo Y**: Normalizado `[0.0, 1.0]` (0.0 = Topo do Quadro, 1.0 = Base/Chão).
- **Eixo Z (Profundidade)**: Escalar absoluto em milímetros `[500mm, 2200mm]`.
  - Distância ideal de jogo: `1400mm - 1700mm`.
  - Modo Proximidade: Ativado automaticamente quando $Z < 880mm$ com histerese até $1020mm$.

## 2. Orçamento Físico e Limites de Hardware (Sanbot RK3288 / Android 6)
- **Zero-Allocation no Loop Principal**: É estritamente proibido instanciar objetos (`new Object()`, `new short[]`, `new Rect()`, `new ArrayList()`) dentro do método `processDepthFrame()`. Todos os buffers devem ser pré-alocados ou reutilizáveis para evitar pausas do Garbage Collector do Android.
- **Orçamento Temporal (Frame Budget)**:
  - Latência máxima de processamento: $\le 25ms$ por frame (garantindo 30 FPS estáveis).
  - Alvo no Desktop (Linux Fedora): $\le 3ms$ por frame.
- **Complexidade de Algoritmo**: $O(N)$ em passada única com amostragem em grade (`step = Math.max(1, width / 64)`).

## 3. Contrato de Detecção Anatômica
- **Cabeça**: Varredura na coluna central estreita (`|x - centroidX| <= bodyW * 0.18f`). Impede que mãos erguidas sejam confundidas com o topo da cabeça.
- **Mãos e Membros**: Rastreamento da extremidade lateral mais externa (`scan.leftExtremityY` / `scan.rightExtremityY`).
- **Postura Sentada**: Identificada quando `minY > 0.28 * height` e `aspectHW < 0.75`.

## 4. Contrato Cinemático (ver `COORDINATE_FRAMES.md` — esta seção não pode divergir)
- **Yaw da cabeça** (fonte da verdade: `CoordinateContractTest`):
  - $X < 0.5$ (esquerda da câmera) $\to$ `targetHeadYaw > 0` $\to$ hardware yaw $> 90^\circ$.
  - $X > 0.5$ (direita da câmera) $\to$ `targetHeadYaw < 0` $\to$ hardware yaw $< 90^\circ$.
- **Controle destravado**: `ACTION_NO_LOCK` em tracking contínuo; `ACTION_BOTH_LOCK` só em `resetCenter()`.
- **EMA** $\alpha = 0.35$ nos ângulos da cabeça. Não empilhar outro filtro sem evidência de logcat.
- **Asas**: `MIRROR_UPDATE_MIN_MS = 150` (ângulo proporcional) e debounce binário 350 ms (UP/DOWN).
- **Fusão RGB+Depth**: MoveNet TFLite + Astra. Swap L/R anatômico só em `PoseDepthFusion`. Proibido `1.0f - x` no decoder.
