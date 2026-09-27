package com.felipe.elftemplate.tracking;

/**
 * Viewport do preview RGB (16:9, center-crop) e do overlay Astra (4:3).
 *
 * <p>Kinect SDK: juntas vivem no depth space. Overlay em color exige CoordinateMapper — FOV e
 * baseline RGB≠depth. No Sanbot a HD está na cabeça (yaw) e a Astra no peito: sem mapper
 * esticar 4:3 no 16:9 desloca o esqueleto (usuário em X, desenho em Y).
 *
 * <p>Espelho: letterbox 4:3 + pan do yaw da cabeça (mesmo ganho de {@code MirrorGameEngine}).
 * Sem {@code 1.0f - x}.
 */
public final class PreviewViewport {

  /** {@code MirrorGameEngine}: yaw = (deltaX) * -GAIN. panNorm = yaw / GAIN. */
  public static final float YAW_TO_PAN_GAIN = 60f;

  private PreviewViewport() {}

  /**
   * Center-crop de {@code srcW×srcH} em {@code viewW×viewH}. Escreve left, top, right, bottom em
   * {@code ltrb} (length ≥ 4).
   */
  public static void centerCropDest(int viewW, int viewH, int srcW, int srcH, int[] ltrb) {
    if (!validBox(viewW, viewH, srcW, srcH, ltrb)) {
      return;
    }
    float viewAspect = (float) viewW / (float) viewH;
    float srcAspect = (float) srcW / (float) srcH;
    if (srcAspect > viewAspect) {
      int scaledW = (int) (viewH * srcAspect);
      int left = (viewW - scaledW) / 2;
      ltrb[0] = left;
      ltrb[1] = 0;
      ltrb[2] = left + scaledW;
      ltrb[3] = viewH;
    } else {
      int scaledH = (int) (viewW / srcAspect);
      int top = (viewH - scaledH) / 2;
      ltrb[0] = 0;
      ltrb[1] = top;
      ltrb[2] = viewW;
      ltrb[3] = top + scaledH;
    }
  }

  /**
   * Letterbox (contain) de {@code srcW×srcH} em {@code viewW×viewH}: barras nas laterais ou no
   * topo/base, aspecto do sensor intacto. Kinect BodyBasics sobre DepthStream.
   */
  public static void letterboxDest(int viewW, int viewH, int srcW, int srcH, int[] ltrb) {
    if (!validBox(viewW, viewH, srcW, srcH, ltrb)) {
      return;
    }
    float viewAspect = (float) viewW / (float) viewH;
    float srcAspect = (float) srcW / (float) srcH;
    if (srcAspect > viewAspect) {
      int scaledH = (int) (viewW / srcAspect);
      int top = (viewH - scaledH) / 2;
      ltrb[0] = 0;
      ltrb[1] = top;
      ltrb[2] = viewW;
      ltrb[3] = top + scaledH;
    } else {
      int scaledW = (int) (viewH * srcAspect);
      int left = (viewW - scaledW) / 2;
      ltrb[0] = left;
      ltrb[1] = 0;
      ltrb[2] = left + scaledW;
      ltrb[3] = viewH;
    }
  }

  public static float panNormFromHeadYaw(int yawOffsetDeg) {
    return yawOffsetDeg / YAW_TO_PAN_GAIN;
  }

  public static float mapX(float nx, int left, int right) {
    return mapX(nx, left, right, 0f);
  }

  /**
   * {@code panNorm} &gt; 0: cabeça virou para a esquerda da Astra (yaw positivo) e o RGB
   * recentra o jogador — junta desloca para a direita no overlay.
   */
  public static float mapX(float nx, int left, int right, float panNorm) {
    return left + (nx + panNorm) * (right - left);
  }

  public static float mapY(float ny, int top, int bottom) {
    return top + ny * (bottom - top);
  }

  private static boolean validBox(int viewW, int viewH, int srcW, int srcH, int[] ltrb) {
    return ltrb != null && ltrb.length >= 4 && viewW > 0 && viewH > 0 && srcW > 0 && srcH > 0;
  }
}
