package com.felipe.elftemplate.tracking3d;

import java.util.Arrays;

/**
 * Armazenamento em crescimento dos nós e folhas durante o treino.
 *
 * <p>As árvores da floresta compartilham os mesmos arrays; cada raiz é só um índice de entrada. Isso
 * é o que permite serializar a floresta como um bloco contíguo e carregá-la no robô sem instanciar um
 * objeto por nó.
 *
 * <p>Distribuição de folha quantizada em byte na hora de gravar, não depois: manter as contagens em
 * float até o fim dobraria a memória do treino sem melhorar o modelo, já que 1/255 já é mais fino que
 * o erro do classificador.
 */
final class ForestNodeBuffer {

  private float[] offsets = new float[0];
  private float[] thresholds = new float[0];
  private int[] left = new int[0];
  private int[] right = new int[0];
  private int nodeCount;

  private byte[] leaves = new byte[0];
  private int leafCount;

  void reset() {
    offsets = new float[0];
    thresholds = new float[0];
    left = new int[0];
    right = new int[0];
    nodeCount = 0;
    leaves = new byte[0];
    leafCount = 0;
  }

  /** Reserva um nó interno com a feature escolhida e devolve o índice dele. */
  int addNode(float[] featureOffsets, float threshold) {
    if (nodeCount == thresholds.length) {
      int grown = Math.max(64, nodeCount * 2);
      offsets = Arrays.copyOf(offsets, grown * 4);
      thresholds = Arrays.copyOf(thresholds, grown);
      left = Arrays.copyOf(left, grown);
      right = Arrays.copyOf(right, grown);
    }
    int node = nodeCount++;
    int base = node * 4;
    offsets[base] = featureOffsets[0];
    offsets[base + 1] = featureOffsets[1];
    offsets[base + 2] = featureOffsets[2];
    offsets[base + 3] = featureOffsets[3];
    thresholds[node] = threshold;
    return node;
  }

  void setChildren(int node, int leftChild, int rightChild) {
    left[node] = leftChild;
    right[node] = rightChild;
  }

  /** Grava a distribuição normalizada de uma folha e devolve a referência codificada. */
  int addLeaf(int[] histogram) {
    if ((leafCount + 1) * BodyPart.COUNT > leaves.length) {
      int grown = Math.max(64, (leafCount + 1) * 2);
      leaves = Arrays.copyOf(leaves, grown * BodyPart.COUNT);
    }
    int total = 0;
    for (int part = 0; part < BodyPart.COUNT; part++) {
      total += histogram[part];
    }
    int base = leafCount * BodyPart.COUNT;
    if (total > 0) {
      for (int part = 0; part < BodyPart.COUNT; part++) {
        int quantized = Math.round((histogram[part] / (float) total) * 255f);
        leaves[base + part] = (byte) Math.min(255, quantized);
      }
    }
    return BodyPartForest.encodeLeaf(leafCount++);
  }

  /** Transfere os arrays já recortados no tamanho exato para o modelo. */
  void publishTo(BodyPartForest forest, int[] roots) {
    forest.assign(
        roots,
        Arrays.copyOf(offsets, nodeCount * 4),
        Arrays.copyOf(thresholds, nodeCount),
        Arrays.copyOf(left, nodeCount),
        Arrays.copyOf(right, nodeCount),
        Arrays.copyOf(leaves, leafCount * BodyPart.COUNT));
  }

  int getNodeCount() {
    return nodeCount;
  }

  int getLeafCount() {
    return leafCount;
  }
}
