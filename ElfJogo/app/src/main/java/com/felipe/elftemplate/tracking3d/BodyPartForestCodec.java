package com.felipe.elftemplate.tracking3d;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Serialização binária da floresta de partes do corpo.
 *
 * <p>Formato próprio, e não um framework de serialização, por três motivos práticos: o Android 6 não
 * traz nada moderno, o arquivo precisa abrir sem alocar objeto por nó, e o modelo é a única coisa
 * neste projeto que atravessa a fronteira treino (JVM do host) → execução (robô). Um formato explícito
 * documenta essa fronteira em vez de esconder.
 *
 * <p>Inteiros e floats em big endian, que é o padrão de {@link DataOutputStream}, então host e device
 * concordam sem configuração.
 */
public final class BodyPartForestCodec {

  /** Sobe junto se o layout mudar; o carregador rejeita versão desconhecida em vez de ler lixo. */
  public static final int VERSION = 1;

  /** Nome do asset dentro do APK. */
  public static final String ASSET_NAME = "bodypart_forest.bin";

  private BodyPartForestCodec() {}

  /**
   * Lê um modelo do stream para dentro de {@code target}.
   *
   * <p>Falha alto em incompatibilidade. Um modelo com número de partes diferente do código atual
   * produziria juntas silenciosamente erradas, o que é muito pior que não carregar.
   */
  public static void read(InputStream input, BodyPartForest target) throws IOException {
    DataInputStream data = new DataInputStream(new BufferedInputStream(input));
    verifyHeader(data);
    int treeCount = data.readInt();
    int nodeCount = data.readInt();
    int leafCount = data.readInt();
    if (treeCount <= 0 || nodeCount <= 0 || leafCount <= 0) {
      throw new IOException("floresta vazia: arvores=" + treeCount + " nos=" + nodeCount);
    }
    int[] roots = new int[treeCount];
    for (int tree = 0; tree < treeCount; tree++) {
      roots[tree] = data.readInt();
    }
    float[] offsets = new float[nodeCount * 4];
    float[] thresholds = new float[nodeCount];
    int[] left = new int[nodeCount];
    int[] right = new int[nodeCount];
    readNodes(data, offsets, thresholds, left, right);
    byte[] leaves = new byte[leafCount * BodyPart.COUNT];
    data.readFully(leaves);
    target.assign(roots, offsets, thresholds, left, right, leaves);
  }

  private static void verifyHeader(DataInputStream data) throws IOException {
    int magic = data.readInt();
    if (magic != BodyPartForest.MAGIC) {
      throw new IOException("assinatura invalida no modelo de partes: " + Integer.toHexString(magic));
    }
    int version = data.readInt();
    if (version != VERSION) {
      throw new IOException("versao de modelo incompativel: " + version + " != " + VERSION);
    }
    int partCount = data.readInt();
    if (partCount != BodyPart.COUNT) {
      throw new IOException("taxonomia divergente: modelo tem " + partCount + " partes");
    }
  }

  private static void readNodes(
      DataInputStream data, float[] offsets, float[] thresholds, int[] left, int[] right)
      throws IOException {
    for (int node = 0; node < thresholds.length; node++) {
      int base = node * 4;
      offsets[base] = data.readFloat();
      offsets[base + 1] = data.readFloat();
      offsets[base + 2] = data.readFloat();
      offsets[base + 3] = data.readFloat();
      thresholds[node] = data.readFloat();
      left[node] = data.readInt();
      right[node] = data.readInt();
    }
  }

  /** Grava o modelo. Usado pelo gerador do asset, que roda no host, não no robô. */
  public static void write(OutputStream output, BodyPartForest source) throws IOException {
    DataOutputStream data = new DataOutputStream(new BufferedOutputStream(output));
    data.writeInt(BodyPartForest.MAGIC);
    data.writeInt(VERSION);
    data.writeInt(BodyPart.COUNT);
    int[] roots = source.getTreeRoots();
    data.writeInt(roots.length);
    data.writeInt(source.getNodeCount());
    data.writeInt(source.getLeafCount());
    for (int tree = 0; tree < roots.length; tree++) {
      data.writeInt(roots[tree]);
    }
    writeNodes(data, source);
    data.write(source.getLeafProbabilities());
    data.flush();
  }

  private static void writeNodes(DataOutputStream data, BodyPartForest source) throws IOException {
    float[] offsets = source.getOffsets();
    float[] thresholds = source.getThresholds();
    int[] left = source.getLeftChild();
    int[] right = source.getRightChild();
    for (int node = 0; node < thresholds.length; node++) {
      int base = node * 4;
      data.writeFloat(offsets[base]);
      data.writeFloat(offsets[base + 1]);
      data.writeFloat(offsets[base + 2]);
      data.writeFloat(offsets[base + 3]);
      data.writeFloat(thresholds[node]);
      data.writeInt(left[node]);
      data.writeInt(right[node]);
    }
  }
}
