package com.felipe.elftemplate.tracking3d;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * O modelo embarcado tem de funcionar na montagem real: Astra na cabeça do Elf, a 0,62–0,92 m.
 *
 * <p>O modelo anterior foi treinado com o sensor sorteado entre 0,92 e 1,18 m. Nos mesmos quadros
 * de validação ele acertava 60% dos pixels de corpo na geometria em que foi treinado e 49% na do
 * robô. Estes testes pegam de volta um modelo treinado na montagem errada.
 *
 * <p>Os quadros usam uma semente que o gerador nunca usa, então medem generalização, não memória.
 */
public class BodyPartRobotMountAccuracyTest {

  private static final int FRAMES = 60;
  private static final long HELD_OUT_SEED = 424242L;

  /** Medido 0,617 com o asset atual; o modelo antigo dava 0,488. */
  private static final float MIN_BODY_ACCURACY = 0.58f;

  /**
   * A montagem do robô não pode ficar mais que isto abaixo da legada. O modelo antigo ficava 0,11.
   */
  private static final float MAX_MOUNT_GAP = 0.03f;

  private static BodyPartForest forest;

  @BeforeClass
  public static void loadShippedModel() throws IOException {
    Path asset = Paths.get("src/main/assets").resolve(BodyPartForestCodec.ASSET_NAME);
    if (!Files.exists(asset)) {
      asset = Paths.get("app/src/main/assets").resolve(BodyPartForestCodec.ASSET_NAME);
    }
    Assume.assumeTrue("modelo de partes ausente", Files.exists(asset));
    forest = new BodyPartForest();
    InputStream input = Files.newInputStream(asset);
    try {
      BodyPartForestCodec.read(input, forest);
    } finally {
      input.close();
    }
  }

  @Test
  public void shippedModelLabelsBodyPixelsWithTheSensorOnTheHead() {
    float accuracy = bodyAccuracy(RandomPoseSampler.ROBOT_HEAD);
    assertTrue(
        String.format("acerto nos pixels de corpo %.3f, piso %.2f", accuracy, MIN_BODY_ACCURACY),
        accuracy >= MIN_BODY_ACCURACY);
  }

  @Test
  public void robotMountIsNotWorseThanTheLegacyMount() {
    float robot = bodyAccuracy(RandomPoseSampler.ROBOT_HEAD);
    float legacy = bodyAccuracy(RandomPoseSampler.LEGACY_TORSO);
    assertTrue(
        String.format(
            "cabeça %.3f contra legada %.3f: treinado na montagem errada?", robot, legacy),
        robot >= legacy - MAX_MOUNT_GAP);
  }

  private static float bodyAccuracy(RandomPoseSampler.Mount mount) {
    BodyPartTrainingSet heldOut = new BodyPartTrainingSet(mount);
    heldOut.generate(FRAMES, HELD_OUT_SEED);
    float[] posterior = new float[BodyPart.COUNT];
    int body = 0;
    int correct = 0;
    for (int sample = 0; sample < heldOut.getSampleCount(); sample++) {
      int label = heldOut.labelOf(sample);
      if (!BodyPart.isBody(label)) {
        continue;
      }
      heldOut.focusFrame(heldOut.frameOf(sample));
      int predicted =
          forest.classify(
              heldOut,
              heldOut.xOf(sample),
              heldOut.yOf(sample),
              heldOut.depthOf(sample),
              posterior);
      body++;
      if (predicted == label) {
        correct++;
      }
    }
    return body == 0 ? 0f : correct / (float) body;
  }
}
