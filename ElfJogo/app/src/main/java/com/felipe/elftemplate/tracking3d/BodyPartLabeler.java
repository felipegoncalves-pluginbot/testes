package com.felipe.elftemplate.tracking3d;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Roda a floresta em cada célula do corpo e guarda a parte mais provável de cada uma.
 *
 * <p>É a etapa "classifica partes do corpo por pixel" do pipeline do Kinect. A diferença de escopo em
 * relação ao artigo é deliberada: lá a floresta roda em todo pixel do quadro na GPU do Xbox; aqui roda
 * só nas células que a segmentação métrica já atribuiu à pessoa. Isso corta o trabalho em mais de dez
 * vezes e é o que torna a coisa viável numa CPU RK3288 em Java. O preço é depender da segmentação, que
 * é justamente a parte do pipeline que já está medida e testada.
 *
 * <p><b>Argmax por célula, e isso foi medido.</b> Guardar as 25 probabilidades custaria 1,2 MB e um
 * laço de mean shift vinte e cinco vezes mais longo. A alternativa intermediária — guardar as duas
 * melhores partes e deixar a célula votar nas duas — foi implementada e <b>rejeitada por medição</b>:
 * o punho em T-pose passou de poucos centímetros para 29 cm de erro. O motivo é estrutural. Para uma
 * parte terminal como a mão, a segunda opção das células do antebraço é justamente "mão", e não existe
 * nada além da ponta dos dedos para equilibrar; o voto secundário é sistematicamente enviesado para
 * dentro do membro. Vale a pena ler isto antes de tentar de novo.
 *
 * <p>A média entre árvores, onde está o ganho do ensemble, acontece antes, em {@link
 * BodyPartForest#classify}.
 *
 * <p>Memória residente: um byte e um float por célula, ~61 KB em grade 128x96.
 */
public final class BodyPartLabeler {

  private static final String TAG = "BodyPartLabeler";

  /** Abaixo desta probabilidade a célula não vota em nada: classificação sem convicção. */
  private static final float MIN_PART_PROBABILITY = 0.30f;

  private final BodyPartForest forest = new BodyPartForest();
  private final GridDepthSampler sampler = new GridDepthSampler();
  private final float[] posterior = new float[BodyPart.COUNT];
  private final int[] partCellCount = new int[BodyPart.COUNT];

  private byte[] cellPart = new byte[0];
  private float[] cellWeight = new float[0];
  private int labeledCells;
  private boolean modelAvailable;
  private String loadError = "";

  /** Carrega o modelo dos assets do APK. Falha suave: sem modelo, o motor usa só a geometria. */
  public boolean loadFromAssets(Context context) {
    if (context == null) {
      loadError = "contexto nulo";
      return false;
    }
    InputStream input = null;
    try {
      input = context.getAssets().open(BodyPartForestCodec.ASSET_NAME);
      BodyPartForestCodec.read(input, forest);
      modelAvailable = forest.isLoaded();
      loadError = modelAvailable ? "" : "modelo vazio";
      Log.i(
          TAG,
          "[BODYPART] modelo carregado: arvores="
              + forest.getTreeCount()
              + " nos="
              + forest.getNodeCount()
              + " folhas="
              + forest.getLeafCount()
              + " bytes="
              + forest.getModelBytes());
    } catch (IOException e) {
      modelAvailable = false;
      loadError = String.valueOf(e.getMessage());
      Log.w(TAG, "[BODYPART] sem modelo de partes: " + loadError);
    } finally {
      closeQuietly(input);
    }
    return modelAvailable;
  }

  /** Carrega de um stream qualquer; usado pelos testes com o modelo recém-treinado. */
  public boolean load(InputStream input) throws IOException {
    BodyPartForestCodec.read(input, forest);
    modelAvailable = forest.isLoaded();
    loadError = modelAvailable ? "" : "modelo vazio";
    return modelAvailable;
  }

  private static void closeQuietly(InputStream input) {
    if (input == null) {
      return;
    }
    try {
      input.close();
    } catch (IOException ignored) {
      // Fechar o asset não pode derrubar o tracking.
    }
  }

  public boolean isModelAvailable() {
    return modelAvailable;
  }

  public String getLoadError() {
    return loadError;
  }

  public BodyPartForest getForest() {
    return forest;
  }

  /**
   * Classifica as células do aglomerado informado.
   *
   * @return quantidade de células que receberam uma parte do corpo com convicção suficiente
   */
  public int label(DepthPointCloud cloud, MetricBodySegmenter segmenter, BodyCluster cluster) {
    labeledCells = 0;
    Arrays.fill(partCellCount, 0);
    if (!modelAvailable || cloud == null || segmenter == null || cluster == null) {
      return 0;
    }
    ensureCapacity(cloud.getCellCount());
    Arrays.fill(cellPart, 0, cloud.getCellCount(), (byte) BodyPart.BACKGROUND);
    sampler.bind(cloud);
    if (!sampler.isBound()) {
      return 0;
    }
    int gridWidth = cloud.getGridWidth();
    for (int gy = cluster.minGridY; gy <= cluster.maxGridY; gy++) {
      int rowBase = gy * gridWidth;
      for (int gx = cluster.minGridX; gx <= cluster.maxGridX; gx++) {
        int cell = rowBase + gx;
        if (segmenter.labelAt(cell) != cluster.label) {
          continue;
        }
        classifyCell(cloud, cell, gx, gy);
      }
    }
    return labeledCells;
  }

  private void classifyCell(DepthPointCloud cloud, int cell, int gx, int gy) {
    float depth = cloud.z(cell);
    if (depth <= 0f) {
      return;
    }
    int part = forest.classify(sampler, gx, gy, depth, posterior);
    float probability = posterior[part];
    if (!BodyPart.isBody(part) || probability < MIN_PART_PROBABILITY) {
      return;
    }
    cellPart[cell] = (byte) part;
    cellWeight[cell] = probability;
    partCellCount[part]++;
    labeledCells++;
  }

  private void ensureCapacity(int cells) {
    if (cellPart.length >= cells) {
      return;
    }
    cellPart = new byte[cells];
    cellWeight = new float[cells];
  }

  /** Parte atribuída à célula, ou {@link BodyPart#BACKGROUND} se ela não votou. */
  public int partAt(int cell) {
    if (cell < 0 || cell >= cellPart.length) {
      return BodyPart.BACKGROUND;
    }
    return cellPart[cell] & 0xFF;
  }

  /** Probabilidade da parte vencedora naquela célula. */
  public float weightAt(int cell) {
    if (cell < 0 || cell >= cellWeight.length) {
      return 0f;
    }
    return cellWeight[cell];
  }

  /**
   * Peso com que a célula vota na parte informada; zero se a parte vencedora dela é outra.
   *
   * <p>É este o acessor que o mean shift usa. A pergunta na forma "quanto esta célula acredita nesta
   * parte" mantém a porta aberta para voto distribuído sem que o proponente precise mudar, mas a
   * implementação hoje é deliberadamente exclusiva — ver a nota sobre o voto secundário na
   * documentação da classe.
   */
  public float weightForPart(int cell, int part) {
    if (cell < 0 || cell >= cellWeight.length) {
      return 0f;
    }
    return (cellPart[cell] & 0xFF) == part ? cellWeight[cell] : 0f;
  }

  public int cellCountOf(int part) {
    if (part < 0 || part >= BodyPart.COUNT) {
      return 0;
    }
    return partCellCount[part];
  }

  public int getLabeledCells() {
    return labeledCells;
  }
}
