package com.felipe.elftemplate.tracking3d;

/**
 * Floresta de decisão aleatória que classifica cada pixel de profundidade em uma parte do corpo.
 *
 * <p>É o classificador do Kinect reimplementado: cada árvore desce fazendo comparações de
 * profundidade ({@link DepthPartFeature}) até uma folha, a folha guarda a distribuição de
 * probabilidade sobre as {@link BodyPart#COUNT} partes, e a resposta da floresta é a média das folhas
 * alcançadas. Nenhuma regra geométrica é escrita à mão: a anatomia toda está nos dados de treino.
 *
 * <p><b>Layout em arrays planos.</b> Não existe classe {@code Node}. Um nó é um índice compartilhado
 * por seis arrays primitivos, e uma folha é um bloco contíguo de bytes. Isso evita centenas de
 * milhares de desreferências de objeto por frame no RK3288 e deixa a árvore inteira em poucas linhas
 * de cache.
 *
 * <p><b>Distribuição quantizada em byte.</b> Cada probabilidade de folha ocupa 1 byte em vez de 4. A
 * resolução de 1/255 é muito melhor do que o erro do próprio classificador, e o modelo cabe em
 * dezenas de kilobytes em vez de centenas — relevante no orçamento de memória do robô.
 */
public final class BodyPartForest {

  /** Marca de arquivo, para falhar alto se um asset de outra geração for carregado. */
  public static final int MAGIC = 0x42504631;

  /** Codificação de folha no campo de filho: índice de folha vira negativo. */
  private static final int LEAF_FLAG = Integer.MIN_VALUE;

  private static final float INV_255 = 1f / 255f;

  private int[] treeRoots = new int[0];
  private float[] offsets = new float[0];
  private float[] thresholds = new float[0];
  private int[] leftChild = new int[0];
  private int[] rightChild = new int[0];
  private byte[] leafProbabilities = new byte[0];
  private int nodeCount;
  private int leafCount;

  /** Popula o modelo a partir dos arrays já decodificados. Chamado pelo codec e pelo treinador. */
  void assign(
      int[] roots,
      float[] featureOffsets,
      float[] featureThresholds,
      int[] left,
      int[] right,
      byte[] leaves) {
    this.treeRoots = roots;
    this.offsets = featureOffsets;
    this.thresholds = featureThresholds;
    this.leftChild = left;
    this.rightChild = right;
    this.leafProbabilities = leaves;
    this.nodeCount = featureThresholds.length;
    this.leafCount = BodyPart.COUNT == 0 ? 0 : leaves.length / BodyPart.COUNT;
  }

  public boolean isLoaded() {
    return treeRoots.length > 0 && nodeCount > 0 && leafCount > 0;
  }

  public int getTreeCount() {
    return treeRoots.length;
  }

  public int getNodeCount() {
    return nodeCount;
  }

  public int getLeafCount() {
    return leafCount;
  }

  /** Bytes residentes do modelo, para o orçamento de memória e a telemetria. */
  public int getModelBytes() {
    return (treeRoots.length * 4)
        + (offsets.length * 4)
        + (thresholds.length * 4)
        + (leftChild.length * 4)
        + (rightChild.length * 4)
        + leafProbabilities.length;
  }

  static int encodeLeaf(int leafIndex) {
    return LEAF_FLAG | leafIndex;
  }

  static boolean isLeaf(int child) {
    return (child & LEAF_FLAG) != 0;
  }

  static int leafIndexOf(int child) {
    return child & ~LEAF_FLAG;
  }

  /**
   * Classifica um pixel e <b>acumula</b> a distribuição média das árvores em {@code posterior}.
   *
   * <p>O chamador zera o vetor antes; acumular em buffer de fora é o que mantém o laço quente sem
   * alocação. O vetor sai normalizado pela quantidade de árvores.
   *
   * @return a parte de maior probabilidade, ou {@link BodyPart#BACKGROUND} se o modelo não carregou
   */
  public int classify(
      DepthPartFeature.DepthSampler sampler, int x, int y, float depthMeters, float[] posterior) {
    for (int part = 0; part < BodyPart.COUNT; part++) {
      posterior[part] = 0f;
    }
    if (!isLoaded() || depthMeters <= 0f) {
      return BodyPart.BACKGROUND;
    }
    for (int tree = 0; tree < treeRoots.length; tree++) {
      int leaf = descend(sampler, x, y, depthMeters, treeRoots[tree]);
      accumulateLeaf(leaf, posterior);
    }
    float scale = 1f / treeRoots.length;
    int best = BodyPart.BACKGROUND;
    float bestValue = -1f;
    for (int part = 0; part < BodyPart.COUNT; part++) {
      posterior[part] = posterior[part] * scale;
      if (posterior[part] > bestValue) {
        bestValue = posterior[part];
        best = part;
      }
    }
    return best;
  }

  /** Desce uma árvore até a folha e devolve o índice dela. */
  private int descend(
      DepthPartFeature.DepthSampler sampler, int x, int y, float depthMeters, int root) {
    int node = root;
    while (true) {
      float response =
          DepthPartFeature.evaluate(sampler, x, y, depthMeters, offsets, node * 4);
      int child = response < thresholds[node] ? leftChild[node] : rightChild[node];
      if (isLeaf(child)) {
        return leafIndexOf(child);
      }
      node = child;
    }
  }

  private void accumulateLeaf(int leaf, float[] posterior) {
    int base = leaf * BodyPart.COUNT;
    for (int part = 0; part < BodyPart.COUNT; part++) {
      posterior[part] = posterior[part] + ((leafProbabilities[base + part] & 0xFF) * INV_255);
    }
  }

  int[] getTreeRoots() {
    return treeRoots;
  }

  float[] getOffsets() {
    return offsets;
  }

  float[] getThresholds() {
    return thresholds;
  }

  int[] getLeftChild() {
    return leftChild;
  }

  int[] getRightChild() {
    return rightChild;
  }

  byte[] getLeafProbabilities() {
    return leafProbabilities;
  }
}
