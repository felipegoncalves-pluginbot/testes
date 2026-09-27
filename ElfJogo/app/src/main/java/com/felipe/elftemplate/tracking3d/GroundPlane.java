package com.felipe.elftemplate.tracking3d;

/**
 * Referencial do mundo ancorado no chão: altura da câmera + pitch de montagem.
 *
 * <p>Converte o referencial óptico (X direita, Y baixo, Z frente) para um referencial com Y para
 * cima medido a partir do piso. É isso que torna possível falar em "1,72 m de altura" e "pulou 9 cm"
 * em vez de "0,18 do quadro", que era o vocabulário do pipeline antigo e mudava conforme a distância
 * e a inclinação do sensor.
 *
 * <p>Assume roll zero: o Astra é fixado no tronco do Sanbot, sem rotação lateral. Só pitch e altura
 * são estimados.
 *
 * <p>Transformação (θ = pitch, positivo com a câmera apontada para baixo):
 *
 * <pre>
 *   Yw = alturaCamera - (Yc * cosθ + Zc * sinθ)
 *   Zw = Zc * cosθ - Yc * sinθ
 *   Xw = Xc
 * </pre>
 */
public final class GroundPlane {

  /** Altura assumida do Astra no tronco do Sanbot Elf quando o piso não está visível. */
  public static final float DEFAULT_CAMERA_HEIGHT_M = 1.05f;

  /** Pitch assumido quando o piso não está visível (câmera praticamente nivelada). */
  public static final float DEFAULT_PITCH_RAD = 0.0f;

  private float pitchRad = DEFAULT_PITCH_RAD;
  private float cameraHeightM = DEFAULT_CAMERA_HEIGHT_M;
  private float cosPitch = 1.0f;
  private float sinPitch = 0.0f;
  private boolean measured;

  public GroundPlane() {
    set(DEFAULT_PITCH_RAD, DEFAULT_CAMERA_HEIGHT_M, false);
  }

  /** Define pitch/altura e marca se vieram de medição real do piso ou do fallback configurado. */
  public void set(float newPitchRad, float newCameraHeightM, boolean fromMeasurement) {
    this.pitchRad = newPitchRad;
    this.cameraHeightM = newCameraHeightM;
    this.cosPitch = (float) Math.cos(newPitchRad);
    this.sinPitch = (float) Math.sin(newPitchRad);
    this.measured = fromMeasurement;
  }

  /** Mistura suave em direção a uma nova medição, evitando saltos de referencial entre frames. */
  public void blendToward(float newPitchRad, float newCameraHeightM, float alpha) {
    float blendedPitch = (pitchRad * (1f - alpha)) + (newPitchRad * alpha);
    float blendedHeight = (cameraHeightM * (1f - alpha)) + (newCameraHeightM * alpha);
    set(blendedPitch, blendedHeight, true);
  }

  public float getPitchRad() {
    return pitchRad;
  }

  public float getPitchDeg() {
    return (float) Math.toDegrees(pitchRad);
  }

  public float getCameraHeightM() {
    return cameraHeightM;
  }

  /** True quando pitch/altura vieram de um piso efetivamente detectado no frame. */
  public boolean isMeasured() {
    return measured;
  }

  /**
   * Altura acima do piso, em metros, para um ponto no referencial óptico.
   *
   * <p>Positivo para cima. O piso fica em 0.
   */
  public float heightAboveFloor(float camYMeters, float camZMeters) {
    return cameraHeightM - ((camYMeters * cosPitch) + (camZMeters * sinPitch));
  }

  /** Distância horizontal (projetada no piso) até o ponto, em metros. */
  public float horizontalDepth(float camYMeters, float camZMeters) {
    return (camZMeters * cosPitch) - (camYMeters * sinPitch);
  }

  /**
   * Inverte a transformação: dado Yw (altura no mundo) e Zw (distância horizontal), devolve o Yc
   * óptico correspondente.
   *
   * <p>Necessário para projetar joints sintetizados no mundo de volta para coordenadas de imagem.
   */
  public float cameraYFromWorld(float worldYMeters, float worldZMeters) {
    float deltaY = cameraHeightM - worldYMeters;
    return (deltaY * cosPitch) - (worldZMeters * sinPitch);
  }

  /** Inverte a transformação para obter o Zc óptico. */
  public float cameraZFromWorld(float worldYMeters, float worldZMeters) {
    float deltaY = cameraHeightM - worldYMeters;
    return (deltaY * sinPitch) + (worldZMeters * cosPitch);
  }

  /** Altura acima do piso para a célula informada da nuvem. */
  public float heightAboveFloor(DepthPointCloud cloud, int index) {
    return heightAboveFloor(cloud.y(index), cloud.z(index));
  }

  /** Distância horizontal para a célula informada da nuvem. */
  public float horizontalDepth(DepthPointCloud cloud, int index) {
    return horizontalDepth(cloud.y(index), cloud.z(index));
  }
}
