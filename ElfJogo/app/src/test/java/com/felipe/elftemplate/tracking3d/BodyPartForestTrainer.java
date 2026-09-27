package com.felipe.elftemplate.tracking3d;

import java.util.Arrays;
import java.util.Random;

/**
 * Treina a floresta de decisão que classifica partes do corpo por pixel.
 *
 * <p>Segue o procedimento de Shotton et al. (CVPR 2011): em cada nó sorteiam-se features candidatas
 * ({@link DepthPartFeature}) e limiares, escolhe-se o par que maximiza o ganho de informação sobre as
 * amostras que chegaram ali, e desce-se recursivamente. Nenhuma regra de anatomia é escrita à mão — o
 * que a árvore sabe vem apenas dos rótulos dos pixels.
 *
 * <p><b>Por que sortear candidatos em vez de otimizar.</b> O espaço de features é contínuo e enorme:
 * quatro deslocamentos reais mais um limiar. Buscar o ótimo em cada nó seria caro e superajustaria.
 * Amostrar algumas centenas e ficar com o melhor é justamente o que descorrelaciona as árvores, e é
 * dessa descorrelação que a floresta tira a capacidade de generalizar.
 *
 * <p><b>Particionamento no lugar.</b> Índices, respostas e rótulos vivem em três arrays paralelos;
 * cada nó recebe uma faixa {@code [from, to)} e a reordena. Uma árvore de profundidade 12 alocaria
 * milhares de sub-arrays sem isso, e o treino ficaria dominado pelo coletor de lixo.
 *
 * <p>Roda no host, nunca no robô: o produto é o arquivo de modelo.
 */
final class BodyPartForestTrainer {

  /** Alcance máximo dos deslocamentos sorteados, em metros no corpo. */
  private static final float MAX_OFFSET_M = 0.70f;

  /** Ganho abaixo disto não justifica um nó: vira folha. */
  private static final float MIN_GAIN = 1e-4f;

  private final SplitCriterion criterion = new SplitCriterion();
  private final ForestNodeBuffer buffer = new ForestNodeBuffer();
  private final float[] candidateOffsets = new float[4];
  private final float[] bestOffsets = new float[4];
  private final int[] histogram = new int[BodyPart.COUNT];

  private int treeCount = 3;
  private int maxDepth = 12;
  private int candidateFeatures = 120;
  private int candidateThresholds = 6;
  private int minSamplesToSplit = 45;

  private BodyPartTrainingSet data;
  private Random random;
  private int[] sampleIndex;
  private int[] labels;
  private float[] responses;

  /** Menor resposta da faixa em avaliação; par de saída de {@link #responseSpan}. */
  private float minResponse;

  void configure(int trees, int depth, int features, int thresholds, int minSamples) {
    this.treeCount = trees;
    this.maxDepth = depth;
    this.candidateFeatures = features;
    this.candidateThresholds = thresholds;
    this.minSamplesToSplit = minSamples;
  }

  /** Treina a floresta inteira e devolve o modelo pronto para inferência ou serialização. */
  BodyPartForest train(BodyPartTrainingSet trainingSet, long seed, BodyPartForest target) {
    this.data = trainingSet;
    this.random = new Random(seed);
    int total = trainingSet.getSampleCount();
    this.responses = new float[total];
    this.labels = new int[total];
    this.sampleIndex = new int[total];
    buffer.reset();
    int[] roots = new int[treeCount];
    for (int tree = 0; tree < treeCount; tree++) {
      prepareBootstrap(total);
      roots[tree] = grow(0, total, 0);
    }
    buffer.publishTo(target, roots);
    return target;
  }

  /**
   * Sorteia com reposição as amostras desta árvore.
   *
   * <p>Bagging clássico: cada árvore vê um recorte diferente dos dados, então elas erram em lugares
   * diferentes e a média fica melhor que qualquer uma isolada.
   */
  private void prepareBootstrap(int total) {
    for (int i = 0; i < total; i++) {
      int chosen = random.nextInt(total);
      sampleIndex[i] = chosen;
      labels[i] = data.labelOf(chosen);
    }
  }

  /** Cresce um nó e devolve a referência de filho: índice de nó interno ou folha codificada. */
  private int grow(int from, int to, int depth) {
    int count = to - from;
    fillHistogram(from, to);
    if (depth >= maxDepth || count < minSamplesToSplit || SplitCriterion.isPure(histogram)) {
      return buffer.addLeaf(histogram);
    }
    float parentEntropy = SplitCriterion.entropy(histogram, count);
    float bestGain = 0f;
    float bestThreshold = 0f;
    boolean found = false;
    for (int candidate = 0; candidate < candidateFeatures; candidate++) {
      drawOffsets();
      computeResponses(candidateOffsets, from, to);
      float span = responseSpan(from, to);
      if (span <= 1e-5f) {
        continue;
      }
      for (int t = 0; t < candidateThresholds; t++) {
        float threshold = minResponse + (random.nextFloat() * span);
        float gain = criterion.gain(responses, labels, from, to, threshold, parentEntropy);
        if (gain > bestGain) {
          bestGain = gain;
          bestThreshold = threshold;
          System.arraycopy(candidateOffsets, 0, bestOffsets, 0, 4);
          found = true;
        }
      }
    }
    if (!found || bestGain < MIN_GAIN) {
      return buffer.addLeaf(histogram);
    }
    return splitNode(from, to, depth, bestThreshold);
  }

  /** Cria o nó interno, reparticiona as amostras e cresce os dois filhos. */
  private int splitNode(int from, int to, int depth, float threshold) {
    computeResponses(bestOffsets, from, to);
    int boundary = partition(from, to, threshold);
    if (boundary == from || boundary == to) {
      fillHistogram(from, to);
      return buffer.addLeaf(histogram);
    }
    int node = buffer.addNode(bestOffsets, threshold);
    int left = grow(from, boundary, depth + 1);
    int right = grow(boundary, to, depth + 1);
    buffer.setChildren(node, left, right);
    return node;
  }

  private float responseSpan(int from, int to) {
    minResponse = Float.MAX_VALUE;
    float max = -Float.MAX_VALUE;
    for (int i = from; i < to; i++) {
      if (responses[i] < minResponse) {
        minResponse = responses[i];
      }
      if (responses[i] > max) {
        max = responses[i];
      }
    }
    return max - minResponse;
  }

  /**
   * Reordena {@code [from, to)} deixando as respostas abaixo do limiar na frente.
   *
   * <p>Índice, resposta e rótulo viajam juntos, porque quem pergunta depois qual amostra está de que
   * lado é o filho, e ele só recebe a faixa.
   */
  private int partition(int from, int to, float threshold) {
    int left = from;
    for (int i = from; i < to; i++) {
      if (responses[i] < threshold) {
        swap(i, left);
        left++;
      }
    }
    return left;
  }

  private void swap(int first, int second) {
    int tempIndex = sampleIndex[first];
    sampleIndex[first] = sampleIndex[second];
    sampleIndex[second] = tempIndex;
    int tempLabel = labels[first];
    labels[first] = labels[second];
    labels[second] = tempLabel;
    float tempResponse = responses[first];
    responses[first] = responses[second];
    responses[second] = tempResponse;
  }

  private void computeResponses(float[] offsets, int from, int to) {
    for (int i = from; i < to; i++) {
      int sample = sampleIndex[i];
      data.focusFrame(data.frameOf(sample));
      responses[i] =
          DepthPartFeature.evaluate(
              data, data.xOf(sample), data.yOf(sample), data.depthOf(sample), offsets, 0);
    }
  }

  private void drawOffsets() {
    for (int i = 0; i < 4; i++) {
      candidateOffsets[i] = ((random.nextFloat() * 2f) - 1f) * MAX_OFFSET_M;
    }
  }

  private void fillHistogram(int from, int to) {
    Arrays.fill(histogram, 0);
    for (int i = from; i < to; i++) {
      histogram[labels[i]]++;
    }
  }

  int getNodeCount() {
    return buffer.getNodeCount();
  }

  int getLeafCount() {
    return buffer.getLeafCount();
  }
}
