package com.felipe.elftemplate.logic;

/**
 * Faz a cabeça do Sanbot olhar para o jogador sabendo que o Astra está na própria cabeça.
 *
 * <p>O controle antigo convertia o X do jogador na imagem direto em yaw absoluto ({@code 90 + (0,5
 * − x)·60}). Com a câmera fixa isso funciona; com a câmera na cabeça, girar a cabeça move o jogador
 * na imagem, o comando volta para o centro e a cabeça oscila. O ganho da malha é ~1 (60° por quadro
 * contra 58,4° de FOV) e a visão chega atrasada em relação ao servo, então a oscilação não
 * amortece.
 *
 * <p>Aqui o erro visual vira um <b>passo</b> sobre o yaw já comandado (servo visual com ação
 * integral), só quando o jogador sai da zona central, e o próximo passo espera a cabeça parar e os
 * frames novos chegarem. Fora disso a cabeça fica parada, como a câmera do Kinect: movimento da
 * câmera suja a nuvem de pontos e vira velocidade falsa de mão para os gestos.
 *
 * <p>Só yaw. Girar em torno do eixo vertical não muda altura nem pitch do sensor, então o plano do
 * chão estimado continua válido; inclinar a cabeça invalidaria o plano até a próxima reestimativa.
 *
 * <p>Sinal: o contrato de {@code COORDINATE_FRAMES.md} (X &lt; 0,5 → yaw positivo → hardware &gt;
 * 90°) continua valendo, agora como direção do passo.
 */
public final class HeadGazeServo {

  /** FOV horizontal do depth do Astra; mesmo valor de {@code AstraIntrinsics}. */
  static final float DEPTH_FOV_H_DEG = 58.4f;

  /** Meia largura da zona central, em fração do quadro (~7° para cada lado). */
  static final float DEADZONE_X = 0.12f;

  /**
   * Fração do erro corrigida por passo; abaixo de 1 para não passar do ponto com erro de medida.
   */
  static final float STEP_GAIN = 0.7f;

  /** Maior passo por comando, em graus. */
  static final float MAX_STEP_DEG = 20f;

  /**
   * Espera entre passos: a cabeça se mover e a visão (~100–200 ms de atraso) enxergar o resultado.
   */
  static final long SETTLE_MS = 600L;

  /** Faixa de yaw relativa ao centro (hardware 30°–150°). */
  static final float MAX_YAW_DEG = 60f;

  private float yawDeg;
  private long lastStepMs;
  private boolean stepped;

  /**
   * Atualiza o alvo com a posição do jogador na imagem.
   *
   * @param targetX X normalizado do jogador no quadro do Astra (0 = esquerda do sensor).
   * @return yaw comandado em graus relativos ao centro, igual ao anterior se não houve passo.
   */
  public synchronized int update(float targetX, long nowMs) {
    float error = 0.5f - targetX;
    if (Math.abs(error) <= DEADZONE_X) {
      return getYawOffset();
    }
    if (stepped && (nowMs - lastStepMs) < SETTLE_MS) {
      return getYawOffset();
    }
    float step = error * DEPTH_FOV_H_DEG * STEP_GAIN;
    step = Math.max(-MAX_STEP_DEG, Math.min(MAX_STEP_DEG, step));
    yawDeg = Math.max(-MAX_YAW_DEG, Math.min(MAX_YAW_DEG, yawDeg + step));
    lastStepMs = nowMs;
    stepped = true;
    return getYawOffset();
  }

  /** Yaw comandado atual, em graus relativos ao centro (90° no hardware). */
  public synchronized int getYawOffset() {
    return Math.round(yawDeg);
  }

  /** Assume que a cabeça está no yaw informado, por exemplo depois de outro controle movê-la. */
  public synchronized void syncTo(float yawOffsetDeg) {
    yawDeg = Math.max(-MAX_YAW_DEG, Math.min(MAX_YAW_DEG, yawOffsetDeg));
  }

  /** Volta ao centro e esquece o último passo. */
  public synchronized void reset() {
    yawDeg = 0f;
    stepped = false;
  }
}
