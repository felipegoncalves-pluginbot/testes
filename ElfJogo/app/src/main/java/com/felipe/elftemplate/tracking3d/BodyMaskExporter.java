package com.felipe.elftemplate.tracking3d;

import android.graphics.Color;

/**
 * Exporta a máscara de segmentação da grade decimada como pixels ARGB.
 *
 * <p>Serve para ver no robô exatamente o que o segmentador considera corpo, o que separa "não
 * detectou a pose" de "não segmentou a pessoa". No pipeline antigo o overlay mostrava apenas a
 * silhueta de profundidade bruta, então um erro de segmentação era invisível na tela.
 *
 * <p>Cores: verde para o corpo primário, âmbar para corpos secundários, cinza escuro para pontos
 * válidos fora de qualquer corpo, transparente para pontos sem profundidade.
 */
public final class BodyMaskExporter {

  private static final int PRIMARY_ARGB = Color.argb(220, 0, 255, 120);
  private static final int SECONDARY_ARGB = Color.argb(200, 255, 170, 0);
  private static final int SCENE_ARGB = Color.argb(70, 90, 100, 120);
  private static final int EMPTY_ARGB = Color.argb(0, 0, 0, 0);

  private BodyMaskExporter() {}

  /**
   * Preenche {@code pixels} com a máscara da grade, incluindo o fundo de cena.
   *
   * @return quantidade de pixels escritos, ou 0 se o buffer for pequeno demais.
   */
  public static int export(DepthPointCloud cloud, MetricBodySegmenter segmenter, int[] pixels) {
    return export(cloud, segmenter, pixels, false);
  }

  /**
   * Preenche {@code pixels} com a máscara da grade.
   *
   * @param bodyOnly quando true, os pontos de cena saem transparentes. É o modo do palco do espelho,
   *     onde a máscara fica sobre a imagem da câmera HD: pintar o fundo cobriria o vídeo com um véu
   *     cinza e a silhueta deixaria de se destacar.
   * @return quantidade de pixels escritos, ou 0 se o buffer for pequeno demais.
   */
  public static int export(
      DepthPointCloud cloud, MetricBodySegmenter segmenter, int[] pixels, boolean bodyOnly) {
    if (cloud == null || segmenter == null || pixels == null) {
      return 0;
    }
    int cells = cloud.getCellCount();
    if (pixels.length < cells) {
      return 0;
    }
    int sceneArgb = bodyOnly ? EMPTY_ARGB : SCENE_ARGB;
    int primaryLabel = segmenter.getClusterCount() > 0 ? segmenter.getCluster(0).label : -1;
    for (int i = 0; i < cells; i++) {
      if (!cloud.isValid(i)) {
        pixels[i] = EMPTY_ARGB;
        continue;
      }
      int label = segmenter.labelAt(i);
      if (label != 0 && label == primaryLabel) {
        pixels[i] = PRIMARY_ARGB;
      } else if (label != 0 && isAcceptedBody(segmenter, label)) {
        pixels[i] = SECONDARY_ARGB;
      } else {
        pixels[i] = sceneArgb;
      }
    }
    return cells;
  }

  private static boolean isAcceptedBody(MetricBodySegmenter segmenter, int label) {
    for (int slot = 0; slot < segmenter.getClusterCount(); slot++) {
      if (segmenter.getCluster(slot).label == label) {
        return true;
      }
    }
    return false;
  }
}
