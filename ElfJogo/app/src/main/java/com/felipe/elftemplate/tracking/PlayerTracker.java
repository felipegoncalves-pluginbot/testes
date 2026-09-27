package com.felipe.elftemplate.tracking;

/**
 * Filtra dados de posição do jogador para evitar movimentos bruscos. Implementa uma média móvel
 * exponencial (EMA).
 */
public class PlayerTracker {
  private float filteredX = 0.5f;
  private float filteredWidth = 0.25f;
  private float smoothingFactor = 0.5f; // Aumentado de 0.3 para 0.5 (menos lag)
  private long lastDetectionTime = 0;
  private static final long DETECTION_TIMEOUT_MS = 2000;

  /**
   * Atualiza os dados de rastreamento com novos valores brutos.
   *
   * <p>Exemplo de uso: tracker.update(0.6f, 0.22f);
   *
   * @param rawX Posição horizontal normalizada (0 a 1).
   * @param rawWidth Largura dos ombros normalizada (0 a 1).
   */
  public void update(float rawX, float rawWidth) {
    filteredX = (rawX * smoothingFactor) + (filteredX * (1 - smoothingFactor));
    filteredWidth = (rawWidth * smoothingFactor) + (filteredWidth * (1 - smoothingFactor));
    lastDetectionTime = System.currentTimeMillis();
  }

  /**
   * Retorna se o jogador está sendo detectado no momento.
   *
   * @return true se detectado dentro do tempo limite.
   */
  public boolean isPlayerPresent() {
    return (System.currentTimeMillis() - lastDetectionTime) < DETECTION_TIMEOUT_MS;
  }

  public float getFilteredX() {
    return filteredX;
  }

  public float getFilteredWidth() {
    return filteredWidth;
  }
}
