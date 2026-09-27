package com.felipe.elftemplate.tracking3d;

import java.util.Arrays;

/**
 * Critério de divisão do treino da floresta: entropia de Shannon e ganho de informação.
 *
 * <p>Separado do crescimento da árvore porque é a única parte matemática do treinador, e assim ela
 * pode ser verificada em isolamento contra valores calculados à mão. Uma entropia errada não faz o
 * treino falhar, só produz uma floresta ruim — o tipo de bug que só aparece como acurácia baixa e
 * custa dias para achar.
 *
 * <p>Os histogramas de esquerda e direita são campos reutilizados: o treinador chama {@link #gain}
 * dezenas de milhões de vezes, e alocar dois arrays por chamada dominaria o tempo total.
 */
final class SplitCriterion {

  private static final float LOG2 = (float) Math.log(2);

  private final int[] leftHistogram = new int[BodyPart.COUNT];
  private final int[] rightHistogram = new int[BodyPart.COUNT];
  private int leftCount;
  private int rightCount;

  /**
   * Ganho de informação de dividir {@code [from, to)} no limiar informado.
   *
   * <p>Ganho é a entropia do pai menos a média das entropias dos filhos ponderada pelo tamanho. Vale
   * zero quando um dos lados fica vazio, que é o jeito de rejeitar limiar degenerado sem tratá-lo como
   * caso especial no chamador.
   */
  float gain(
      float[] responses, int[] labels, int from, int to, float threshold, float parentEntropy) {
    Arrays.fill(leftHistogram, 0);
    Arrays.fill(rightHistogram, 0);
    leftCount = 0;
    rightCount = 0;
    for (int i = from; i < to; i++) {
      if (responses[i] < threshold) {
        leftHistogram[labels[i]]++;
        leftCount++;
      } else {
        rightHistogram[labels[i]]++;
        rightCount++;
      }
    }
    if (leftCount == 0 || rightCount == 0) {
      return 0f;
    }
    int total = leftCount + rightCount;
    float weighted =
        ((leftCount / (float) total) * entropy(leftHistogram, leftCount))
            + ((rightCount / (float) total) * entropy(rightHistogram, rightCount));
    return parentEntropy - weighted;
  }

  int getLeftCount() {
    return leftCount;
  }

  int getRightCount() {
    return rightCount;
  }

  /** Entropia de Shannon em bits de um histograma de contagens. */
  static float entropy(int[] histogram, int count) {
    if (count <= 0) {
      return 0f;
    }
    float sum = 0f;
    float inverse = 1f / count;
    for (int i = 0; i < histogram.length; i++) {
      if (histogram[i] == 0) {
        continue;
      }
      float probability = histogram[i] * inverse;
      sum -= probability * ((float) Math.log(probability) / LOG2);
    }
    return sum;
  }

  /** True quando todas as amostras do nó têm o mesmo rótulo: não há o que dividir. */
  static boolean isPure(int[] histogram) {
    int nonZero = 0;
    for (int i = 0; i < histogram.length; i++) {
      if (histogram[i] > 0) {
        nonZero++;
        if (nonZero > 1) {
          return false;
        }
      }
    }
    return true;
  }
}
