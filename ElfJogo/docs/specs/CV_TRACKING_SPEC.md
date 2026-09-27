# ESPECIFICAÇÃO DO PIPELINE DE VISÃO COMPUTACIONAL & TRACKING 3D

## 1. Sistema de Coordenadas e Normalização
- **Eixo X**: Normalizado `[0.0, 1.0]` (0.0 = Extrema Esquerda da Câmera, 1.0 = Extrema Direita).
- **Eixo Y**: Normalizado `[0.0, 1.0]` (0.0 = Topo do Quadro, 1.0 = Base/Chão).
- **Eixo Z (Profundidade)**: Escalar absoluto em milímetros. Motor métrico (`tracking3d`): `[450mm, 4200mm]`; pipeline de blob legado: `[500mm, 2200mm]`.
  - Montagem: Astra e HD **na cabeça**, sensor a ~0,8 m (ver `COORDINATE_FRAMES.md` §0). A cabeça de um adulto só entra no quadro a partir de ~2,3–2,5 m; mais perto o tronco é rastreado sem ela.
  - Modo Proximidade (legado): Ativado automaticamente quando $Z < 880mm$ com histerese até $1020mm$.

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
- **Yaw da cabeça** (fonte da verdade: `CoordinateContractTest`, `HeadGazeServoTest`), como direção do passo:
  - $X < 0.5$ (esquerda da câmera) $\to$ passo de `targetHeadYaw` positivo $\to$ hardware yaw $> 90^\circ$.
  - $X > 0.5$ (direita da câmera) $\to$ passo negativo $\to$ hardware yaw $< 90^\circ$.
  - A câmera está na cabeça: `HeadGazeServo` (zona central ±0,12, passo limitado, 600 ms entre passos). Nada de yaw absoluto a partir de X.
- **Pitch da cabeça**: não é comandado a partir da imagem; inclinar a cabeça desloca o plano do chão.
- **Controle destravado**: `ACTION_NO_LOCK` em tracking contínuo; `ACTION_BOTH_LOCK` só em `resetCenter()`.
- **EMA** $\alpha = 0.35$ só no yaw imitado do modo espelho sentado. Não empilhar outro filtro sem evidência de logcat.
- **Asas**: `MIRROR_UPDATE_MIN_MS = 150` (ângulo proporcional) e debounce binário 350 ms (UP/DOWN).
- **Fusão RGB+Depth**: MoveNet TFLite + Astra. Swap L/R anatômico só em `PoseDepthFusion`. Proibido `1.0f - x` no decoder.
