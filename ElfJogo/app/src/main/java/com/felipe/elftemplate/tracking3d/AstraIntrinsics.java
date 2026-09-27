package com.felipe.elftemplate.tracking3d;

/**
 * Modelo pinhole da câmera de profundidade Orbbec Astra.
 *
 * <p>O pipeline antigo (`tracking/`) nunca teve modelo de câmera: tratava X/Y como frações do
 * quadro e Z como um escalar solto, então a mesma pessoa a 0,8 m e a 2,5 m gerava esqueletos com
 * proporções diferentes. Aqui todo joint nasce em metros no referencial óptico da câmera.
 *
 * <p>FOV de profundidade do Astra (spec Orbbec, série Astra): 58,4° horizontal x 45,5° vertical.
 * Em 640x480 isso dá fx = 320/tan(29,2°) ≈ 572,6 e fy = 240/tan(22,75°) ≈ 572,4, coerente com o
 * valor clássico de sensores PrimeSense (~572).
 *
 * <p>Referencial óptico: X para a direita da imagem, Y para baixo, Z para frente (mesma orientação
 * do buffer de pixels, o que evita espelhamentos acidentais).
 */
public final class AstraIntrinsics {

  /** FOV horizontal do stream de profundidade, em graus. */
  public static final float DEPTH_FOV_H_DEG = 58.4f;

  /** FOV vertical do stream de profundidade, em graus. */
  public static final float DEPTH_FOV_V_DEG = 45.5f;

  private int width;
  private int height;
  private float fx;
  private float fy;
  private float cx;
  private float cy;
  private float invFx;
  private float invFy;

  public AstraIntrinsics() {
    configure(640, 480);
  }

  /**
   * Recalcula fx/fy/cx/cy para a resolução informada.
   *
   * <p>Chamado quando o stream muda de modo (ou quando um fixture roda em resolução reduzida). Como
   * fx escala linearmente com a largura, o mesmo código serve para 640x480 e para grades decimadas.
   */
  public void configure(int frameWidth, int frameHeight) {
    if (frameWidth <= 0 || frameHeight <= 0) {
      return;
    }
    this.width = frameWidth;
    this.height = frameHeight;
    double halfFovH = Math.toRadians(DEPTH_FOV_H_DEG) * 0.5;
    double halfFovV = Math.toRadians(DEPTH_FOV_V_DEG) * 0.5;
    this.fx = (float) ((frameWidth * 0.5) / Math.tan(halfFovH));
    this.fy = (float) ((frameHeight * 0.5) / Math.tan(halfFovV));
    this.cx = (frameWidth - 1) * 0.5f;
    this.cy = (frameHeight - 1) * 0.5f;
    this.invFx = 1.0f / fx;
    this.invFy = 1.0f / fy;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public float getFx() {
    return fx;
  }

  public float getFy() {
    return fy;
  }

  public float getCx() {
    return cx;
  }

  public float getCy() {
    return cy;
  }

  /** Deprojeta a coluna do pixel em X métrico (metros) para uma dada distância em metros. */
  public float deprojectX(float pixelU, float depthMeters) {
    return (pixelU - cx) * depthMeters * invFx;
  }

  /** Deprojeta a linha do pixel em Y métrico (metros, positivo para baixo). */
  public float deprojectY(float pixelV, float depthMeters) {
    return (pixelV - cy) * depthMeters * invFy;
  }

  /** Projeta X métrico de volta para coluna de pixel. */
  public float projectU(float camXMeters, float depthMeters) {
    if (depthMeters <= 0f) {
      return cx;
    }
    return cx + (camXMeters * fx) / depthMeters;
  }

  /** Projeta Y métrico de volta para linha de pixel. */
  public float projectV(float camYMeters, float depthMeters) {
    if (depthMeters <= 0f) {
      return cy;
    }
    return cy + (camYMeters * fy) / depthMeters;
  }

  /** Projeta X métrico para coordenada normalizada [0,1] do quadro (contrato dos jogos atuais). */
  public float projectNormX(float camXMeters, float depthMeters) {
    if (width <= 1) {
      return 0.5f;
    }
    return projectU(camXMeters, depthMeters) / (width - 1);
  }

  /** Projeta Y métrico para coordenada normalizada [0,1] do quadro. */
  public float projectNormY(float camYMeters, float depthMeters) {
    if (height <= 1) {
      return 0.5f;
    }
    return projectV(camYMeters, depthMeters) / (height - 1);
  }

  /**
   * Quantos pixels equivalem a um metro na distância informada.
   *
   * <p>Serve para dimensionar limiares de varredura em metros em vez de frações de quadro, e para
   * escalar o número mínimo de pontos de um blob (que cai com 1/Z²).
   */
  public float pixelsPerMeterAt(float depthMeters) {
    if (depthMeters <= 0f) {
      return fx;
    }
    return fx / depthMeters;
  }
}
