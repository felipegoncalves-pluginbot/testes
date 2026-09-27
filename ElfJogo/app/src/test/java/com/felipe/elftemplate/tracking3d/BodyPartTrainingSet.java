package com.felipe.elftemplate.tracking3d;

import java.util.Random;

/**
 * Conjunto de treino do classificador de partes: grades de profundidade rotuladas e pixels sorteados.
 *
 * <p>Guarda a <b>grade decimada</b>, não o quadro de 640x480, porque é sobre a grade que o
 * classificador roda no robô. Treinar na resolução cheia e inferir na decimada mudaria a escala
 * efetiva de toda feature e jogaria fora a acurácia medida — é o erro clássico de disparidade entre
 * treino e execução.
 *
 * <p>Também implementa {@link DepthPartFeature.DepthSampler}: o treinador aponta {@link
 * #focusFrame(int)} para o quadro do pixel em avaliação e usa exatamente a mesma função de feature
 * que o device usará.
 *
 * <p>Memória: cada grade de 128x96 ocupa 48 KB em float. Algumas centenas de quadros cabem
 * folgadamente na JVM do host, e nada disso vai para o APK — o que é embarcado é só o modelo treinado.
 */
final class BodyPartTrainingSet implements DepthPartFeature.DepthSampler {

  /** Pixels sorteados por quadro. Poucos e de muitos quadros bate muitos de poucos quadros. */
  private static final int PIXELS_PER_FRAME = 220;

  /** Proporção de amostras de fundo; sem fundo o classificador não aprende a silhueta. */
  private static final float BACKGROUND_SHARE = 0.18f;

  private final SyntheticDepthRenderer renderer = new SyntheticDepthRenderer();
  private final LabeledHumanScene scene = new LabeledHumanScene(renderer);
  private final PosedHumanSkeleton body = new PosedHumanSkeleton();
  private final RandomPoseSampler poseSampler = new RandomPoseSampler();
  private final DepthPointCloud cloud = new DepthPointCloud();

  private float[][] frameDepth = new float[0][];
  private byte[][] frameLabel = new byte[0][];
  private int frameCount;

  private int[] sampleFrame = new int[0];
  private int[] sampleX = new int[0];
  private int[] sampleY = new int[0];
  private float[] sampleDepth = new float[0];
  private byte[] sampleLabel = new byte[0];
  private int sampleCount;

  private int gridWidth;
  private int gridHeight;
  private float focalPx;
  private float[] activeDepth;

  /** Gera {@code frames} cenas aleatórias e sorteia pixels de cada uma. */
  void generate(int frames, long seed) {
    Random random = new Random(seed);
    frameDepth = new float[frames][];
    frameLabel = new byte[frames][];
    int capacity = frames * PIXELS_PER_FRAME;
    sampleFrame = new int[capacity];
    sampleX = new int[capacity];
    sampleY = new int[capacity];
    sampleDepth = new float[capacity];
    sampleLabel = new byte[capacity];
    frameCount = 0;
    sampleCount = 0;
    byte[] pixelLabels = new byte[SyntheticDepthRenderer.WIDTH * SyntheticDepthRenderer.HEIGHT];
    for (int frame = 0; frame < frames; frame++) {
      short[] depth = renderFrame(random, pixelLabels);
      storeFrame(depth, pixelLabels);
      sampleFrom(frameCount - 1, random);
    }
  }

  private short[] renderFrame(Random random, byte[] pixelLabels) {
    poseSampler.apply(body, renderer, random);
    scene.emit(body);
    return renderer.renderLabeled(random.nextLong(), pixelLabels);
  }

  /**
   * Decima profundidade e rótulo para a grade e guarda o par.
   *
   * <p>O rótulo da célula vem do pixel primário do bloco de decimação. A profundidade da célula é a
   * mediana de três taps vizinhos, então pode vir de outro tap; a poucos pixels de distância o rótulo
   * é o mesmo em praticamente toda a silhueta, e nas bordas onde difere o próprio sensor é ambíguo.
   */
  private void storeFrame(short[] depth, byte[] pixelLabels) {
    cloud.build(depth, SyntheticDepthRenderer.WIDTH, SyntheticDepthRenderer.HEIGHT);
    gridWidth = cloud.getGridWidth();
    gridHeight = cloud.getGridHeight();
    focalPx = cloud.getIntrinsics().getFx();
    int cells = cloud.getCellCount();
    float[] grid = new float[cells];
    byte[] labels = new byte[cells];
    int decimation = cloud.getDecimation();
    for (int gy = 0; gy < gridHeight; gy++) {
      for (int gx = 0; gx < gridWidth; gx++) {
        int cell = (gy * gridWidth) + gx;
        grid[cell] = cloud.isValid(cell) ? cloud.z(cell) : 0f;
        int sourceIndex = ((gy * decimation) * SyntheticDepthRenderer.WIDTH) + (gx * decimation);
        labels[cell] = grid[cell] > 0f ? pixelLabels[sourceIndex] : (byte) BodyPart.BACKGROUND;
      }
    }
    frameDepth[frameCount] = grid;
    frameLabel[frameCount] = labels;
    frameCount++;
  }

  /**
   * Sorteia pixels do quadro, com cota reservada para o fundo.
   *
   * <p>Um sorteio uniforme sobre a grade traria fundo demais: a pessoa ocupa uma fração pequena do
   * quadro, e a floresta aprenderia a responder "fundo" quase sempre. Separar as cotas equilibra as
   * classes sem precisar de peso por classe no critério de divisão.
   */
  private void sampleFrom(int frame, Random random) {
    float[] grid = frameDepth[frame];
    byte[] labels = frameLabel[frame];
    int backgroundQuota = (int) (PIXELS_PER_FRAME * BACKGROUND_SHARE);
    int bodyQuota = PIXELS_PER_FRAME - backgroundQuota;
    int bodyTaken = 0;
    int backgroundTaken = 0;
    int attempts = 0;
    int maxAttempts = PIXELS_PER_FRAME * 60;
    while ((bodyTaken < bodyQuota || backgroundTaken < backgroundQuota) && attempts < maxAttempts) {
      attempts++;
      int cell = random.nextInt(grid.length);
      if (grid[cell] <= 0f) {
        continue;
      }
      boolean isBody = BodyPart.isBody(labels[cell] & 0xFF);
      if (isBody && bodyTaken >= bodyQuota) {
        continue;
      }
      if (!isBody && backgroundTaken >= backgroundQuota) {
        continue;
      }
      addSample(frame, cell, grid[cell], labels[cell]);
      if (isBody) {
        bodyTaken++;
      } else {
        backgroundTaken++;
      }
    }
  }

  private void addSample(int frame, int cell, float depthMeters, byte label) {
    sampleFrame[sampleCount] = frame;
    sampleX[sampleCount] = cell % gridWidth;
    sampleY[sampleCount] = cell / gridWidth;
    sampleDepth[sampleCount] = depthMeters;
    sampleLabel[sampleCount] = label;
    sampleCount++;
  }

  /** Aponta o sampler para um quadro antes de avaliar features de amostras dele. */
  void focusFrame(int frame) {
    activeDepth = frameDepth[frame];
  }

  @Override
  public int getWidth() {
    return gridWidth;
  }

  @Override
  public int getHeight() {
    return gridHeight;
  }

  @Override
  public float depthAt(int x, int y) {
    float value = activeDepth[(y * gridWidth) + x];
    return value > 0f ? value : DepthPartFeature.BACKGROUND_DEPTH_M;
  }

  @Override
  public float focalLengthPx() {
    return focalPx;
  }

  int getSampleCount() {
    return sampleCount;
  }

  int frameOf(int sample) {
    return sampleFrame[sample];
  }

  int xOf(int sample) {
    return sampleX[sample];
  }

  int yOf(int sample) {
    return sampleY[sample];
  }

  float depthOf(int sample) {
    return sampleDepth[sample];
  }

  int labelOf(int sample) {
    return sampleLabel[sample] & 0xFF;
  }

  int getFrameCount() {
    return frameCount;
  }
}
