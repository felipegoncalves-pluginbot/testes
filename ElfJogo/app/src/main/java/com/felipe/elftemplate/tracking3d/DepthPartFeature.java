package com.felipe.elftemplate.tracking3d;

/**
 * Feature de comparação de profundidade, o núcleo do classificador de partes do corpo.
 *
 * <p>A ideia é de Shotton et al. (CVPR 2011): para um pixel, sorteiam-se dois deslocamentos, lê-se a
 * profundidade nos dois pontos deslocados e a resposta é a diferença. Cada teste é ridiculamente
 * barato — duas leituras e uma subtração — e é justamente por isso que uma floresta com milhares de
 * nós roda em tempo real. O poder não está em uma feature esperta, está em combinar milhares delas.
 *
 * <p><b>Invariância à distância.</b> O deslocamento aplicado na imagem é dividido pela profundidade
 * do pixel consultado. Assim o par de pontos cobre sempre a mesma extensão física do corpo,
 * independentemente de a pessoa estar a 1 m ou a 4 m. Sem essa divisão, a mesma árvore precisaria
 * aprender cada parte em cada distância, e é exatamente esse tipo de dependência de escala que
 * quebrava o pipeline de blob antigo.
 *
 * <p><b>Desvio deliberado do artigo.</b> Lá o deslocamento é parametrizado em pixel·metro, o que
 * amarra o modelo à resolução e à distância focal usadas no treino. Aqui ele é parametrizado em
 * <b>metros no mundo</b> e convertido para pixels com a distância focal do sensor em uso. O modelo
 * treinado continua válido se a grade decimada mudar de tamanho ou se o sensor tiver outra ótica, o
 * que importa num projeto onde a grade é configurável.
 */
public final class DepthPartFeature {

  /**
   * Profundidade atribuída a ponto fora do quadro ou sem leitura, em metros.
   *
   * <p>Um valor grande e fixo faz o fundo parecer "muito longe", que é o comportamento desejado: a
   * silhueta contra o vazio se torna uma feature forte. Shotton usa a mesma convenção. Deixar 0 aqui
   * seria pior, porque 0 significaria "muito perto" e inverteria o sinal da resposta.
   */
  public static final float BACKGROUND_DEPTH_M = 8.0f;

  /**
   * Fonte de profundidade indexável por pixel, em metros.
   *
   * <p>Existe para que treino e execução no robô compartilhem <em>a mesma</em> avaliação de feature.
   * Se o treinador tivesse a sua própria cópia da conta, qualquer divergência de arredondamento ou de
   * convenção de borda apareceria como perda de acurácia inexplicável no device.
   */
  public interface DepthSampler {

    int getWidth();

    int getHeight();

    /** Profundidade em metros, ou {@link #BACKGROUND_DEPTH_M} se o pixel não tem leitura válida. */
    float depthAt(int x, int y);

    /** Distância focal em pixels do mesmo grid que {@link #depthAt} indexa. */
    float focalLengthPx();
  }

  private DepthPartFeature() {}

  /**
   * Resposta da feature no pixel informado, em metros.
   *
   * @param depthMeters profundidade já lida no pixel consultado, para não reler
   * @param offsets vetor de 4 posições {uxM, uyM, vxM, vyM}, deslocamentos em metros no mundo
   * @param base índice inicial do quarteto dentro de {@code offsets}
   */
  public static float evaluate(
      DepthSampler sampler, int x, int y, float depthMeters, float[] offsets, int base) {
    float pixelsPerMeter = sampler.focalLengthPx() / depthMeters;
    float first =
        sampleOffset(sampler, x, y, offsets[base], offsets[base + 1], pixelsPerMeter);
    float second =
        sampleOffset(sampler, x, y, offsets[base + 2], offsets[base + 3], pixelsPerMeter);
    return first - second;
  }

  /**
   * Lê a profundidade num ponto deslocado.
   *
   * <p>O arredondamento é feito com {@code (int) Math.floor}, não com cast direto, porque cast em
   * Java trunca em direção a zero: deslocamento negativo cairia num pixel diferente do positivo
   * equivalente, introduzindo uma assimetria esquerda/direita que o classificador aprenderia como se
   * fosse anatomia.
   */
  private static float sampleOffset(
      DepthSampler sampler, int x, int y, float offsetXm, float offsetYm, float pixelsPerMeter) {
    int sampleX = x + (int) Math.floor(offsetXm * pixelsPerMeter);
    int sampleY = y + (int) Math.floor(offsetYm * pixelsPerMeter);
    if (sampleX < 0 || sampleY < 0 || sampleX >= sampler.getWidth() || sampleY >= sampler.getHeight()) {
      return BACKGROUND_DEPTH_M;
    }
    return sampler.depthAt(sampleX, sampleY);
  }
}
