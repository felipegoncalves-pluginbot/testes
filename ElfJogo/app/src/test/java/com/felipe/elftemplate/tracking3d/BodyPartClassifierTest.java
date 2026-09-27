package com.felipe.elftemplate.tracking3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Valida a mecânica do classificador de partes: ele aprende, e o modelo sobrevive à serialização.
 *
 * <p>Floresta pequena de propósito. O que está sob teste aqui é o maquinário — feature, ganho de
 * informação, crescimento da árvore, codec — não a acurácia do modelo que vai para o robô. Acurácia
 * é medida em {@link BodyPartPoseAccuracyTest}, contra o asset que realmente será embarcado.
 */
public class BodyPartClassifierTest {

  private static final int FRAMES = 14;

  private static BodyPartTrainingSet trainingSet;
  private static BodyPartForest forest;

  @BeforeClass
  public static void trainSmallForest() {
    trainingSet = new BodyPartTrainingSet();
    trainingSet.generate(FRAMES, 7717L);
    BodyPartForestTrainer trainer = new BodyPartForestTrainer();
    trainer.configure(2, 9, 40, 5, 25);
    forest = trainer.train(trainingSet, 991L, new BodyPartForest());
  }

  @Test
  public void trainingProducesAUsableForest() {
    assertTrue("floresta deve carregar", forest.isLoaded());
    assertEquals("duas arvores pedidas", 2, forest.getTreeCount());
    assertTrue("deve haver nos internos: " + forest.getNodeCount(), forest.getNodeCount() > 10);
    assertTrue("deve haver folhas: " + forest.getLeafCount(), forest.getLeafCount() > 10);
  }

  @Test
  public void syntheticSceneProducesLabelledBodyPixels() {
    int bodySamples = 0;
    int distinctParts = 0;
    boolean[] seen = new boolean[BodyPart.COUNT];
    for (int sample = 0; sample < trainingSet.getSampleCount(); sample++) {
      int label = trainingSet.labelOf(sample);
      if (!BodyPart.isBody(label)) {
        continue;
      }
      bodySamples++;
      if (!seen[label]) {
        seen[label] = true;
        distinctParts++;
      }
    }
    assertTrue("deve haver pixels de corpo amostrados: " + bodySamples, bodySamples > 500);
    assertTrue(
        "cena deve exercitar a maioria das partes, viu " + distinctParts, distinctParts >= 20);
  }

  /**
   * O classificador tem de bater o palpite trivial por larga margem.
   *
   * <p>O comparativo é a classe majoritária das próprias amostras, não 1/25. Chutar sempre a classe
   * mais frequente é o que um modelo quebrado faz, e é essa a barra que importa.
   */
  @Test
  public void classifierBeatsTheMajorityClassBaseline() {
    float[] posterior = new float[BodyPart.COUNT];
    int correct = 0;
    int[] labelCounts = new int[BodyPart.COUNT];
    int total = trainingSet.getSampleCount();
    for (int sample = 0; sample < total; sample++) {
      trainingSet.focusFrame(trainingSet.frameOf(sample));
      int predicted =
          forest.classify(
              trainingSet,
              trainingSet.xOf(sample),
              trainingSet.yOf(sample),
              trainingSet.depthOf(sample),
              posterior);
      labelCounts[trainingSet.labelOf(sample)]++;
      if (predicted == trainingSet.labelOf(sample)) {
        correct++;
      }
    }
    float accuracy = correct / (float) total;
    float majority = majorityShare(labelCounts, total);
    assertTrue(
        String.format("acuracia %.3f deve superar a classe majoritaria %.3f", accuracy, majority),
        accuracy > majority + 0.15f);
  }

  private static float majorityShare(int[] labelCounts, int total) {
    int best = 0;
    for (int label = 0; label < labelCounts.length; label++) {
      if (labelCounts[label] > best) {
        best = labelCounts[label];
      }
    }
    return best / (float) total;
  }

  /**
   * O modelo tem de atravessar a serialização sem mudar de resposta.
   *
   * <p>Este é o teste que protege a fronteira treino-execução: o robô nunca vê o objeto treinado, só o
   * arquivo. Um erro de ordem de bytes ou de quantização apareceria como acurácia pior apenas no
   * device, onde é caríssimo de investigar.
   */
  @Test
  public void codecRoundTripPreservesPredictions() throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    BodyPartForestCodec.write(buffer, forest);
    BodyPartForest reloaded = new BodyPartForest();
    BodyPartForestCodec.read(new ByteArrayInputStream(buffer.toByteArray()), reloaded);

    assertEquals("arvores", forest.getTreeCount(), reloaded.getTreeCount());
    assertEquals("nos", forest.getNodeCount(), reloaded.getNodeCount());
    assertEquals("folhas", forest.getLeafCount(), reloaded.getLeafCount());

    float[] first = new float[BodyPart.COUNT];
    float[] second = new float[BodyPart.COUNT];
    int compared = 0;
    for (int sample = 0; sample < trainingSet.getSampleCount(); sample += 17) {
      trainingSet.focusFrame(trainingSet.frameOf(sample));
      int x = trainingSet.xOf(sample);
      int y = trainingSet.yOf(sample);
      float depth = trainingSet.depthOf(sample);
      assertEquals(
          "predicao deve sobreviver ao codec",
          forest.classify(trainingSet, x, y, depth, first),
          reloaded.classify(trainingSet, x, y, depth, second));
      compared++;
    }
    assertTrue("nada comparado", compared > 20);
  }
}
