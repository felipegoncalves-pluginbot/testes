package com.felipe.elftemplate.tracking3d;

import java.util.Random;

/**
 * Renderiza um mapa de profundidade 640x480 realista por ray casting.
 *
 * <p>A cena tem piso, parede de fundo e as cápsulas do corpo. Cada pixel recebe o Z do ponto mais
 * próximo, exatamente como o Astra entrega em {@code PixelFormat.DEPTH_1_MM}. Isso dá perspectiva,
 * auto-oclusão e queda de densidade com a distância — as três coisas que um fixture de retângulos em
 * 64x48 não tem, e que são justamente onde o pipeline antigo quebrava no robô mesmo com os testes
 * verdes.
 */
final class SyntheticDepthRenderer {

  static final int WIDTH = 640;
  static final int HEIGHT = 480;

  /** Distância da parede de fundo, em metros (horizontal, no referencial do piso). */
  private float backWallDepthM = 3.60f;

  /** Coeficiente de ruído axial: sigma = coeff * Z^2, em metros. Astra fica perto de 0,003. */
  private float noiseCoeff = 0.0030f;

  /** Fração de pixels que o sensor devolve como inválidos (0 mm). */
  private float dropoutRatio = 0.02f;

  private final RayCapsuleTracer tracer = new RayCapsuleTracer();
  private final AstraIntrinsics intrinsics = new AstraIntrinsics();
  private final GroundPlane plane = new GroundPlane();

  SyntheticDepthRenderer() {
    intrinsics.configure(WIDTH, HEIGHT);
  }

  void setCamera(float heightM, float pitchDeg) {
    plane.set((float) Math.toRadians(pitchDeg), heightM, true);
  }

  void setBackWallDepthM(float depthM) {
    this.backWallDepthM = depthM;
  }

  void setNoise(float axialCoeff, float dropout) {
    this.noiseCoeff = axialCoeff;
    this.dropoutRatio = dropout;
  }

  GroundPlane getTruthPlane() {
    return plane;
  }

  RayCapsuleTracer getTracer() {
    return tracer;
  }

  AstraIntrinsics getIntrinsics() {
    return intrinsics;
  }

  /** Converte um segmento do referencial do mundo para câmera e o adiciona como cápsula. */
  void addWorldCapsule(
      float x1, float y1, float z1, float x2, float y2, float z2, float radius) {
    addWorldCapsule(x1, y1, z1, x2, y2, z2, radius, BodyPart.BACKGROUND);
  }

  /** Versão rotulada, usada pelo gerador de dados de treino do classificador de partes. */
  void addWorldCapsule(
      float x1,
      float y1,
      float z1,
      float x2,
      float y2,
      float z2,
      float radius,
      int partLabel) {
    tracer.addCapsule(
        x1,
        plane.cameraYFromWorld(y1, z1),
        plane.cameraZFromWorld(y1, z1),
        x2,
        plane.cameraYFromWorld(y2, z2),
        plane.cameraZFromWorld(y2, z2),
        radius,
        partLabel);
  }

  /** Rótulo da cápsula vencedora do último pixel traçado; par de saída de {@link #traceDepth}. */
  private int lastHitLabel;

  /** Renderiza a cena inteira e devolve o buffer de profundidade em milímetros. */
  short[] render(long noiseSeed) {
    return renderLabeled(noiseSeed, null);
  }

  /**
   * Renderiza profundidade e, opcionalmente, o mapa de partes do corpo por pixel.
   *
   * <p>O rótulo é o da cápsula que o raio realmente atingiu, então a verdade de campo do treino vem
   * da mesma geometria que gerou a profundidade. É isso que torna o conjunto de treino consistente:
   * não existe risco de o rótulo discordar da imagem.
   *
   * <p>Um pixel que sofreu dropout do sensor recebe rótulo de fundo mesmo que o raio tenha acertado o
   * corpo, porque no device aquele pixel também não terá leitura. Treinar com o rótulo verdadeiro em
   * pixel sem profundidade ensinaria o classificador a contar com dado que ele não vai receber.
   */
  short[] renderLabeled(long noiseSeed, byte[] labelsOut) {
    short[] depth = new short[WIDTH * HEIGHT];
    Random random = new Random(noiseSeed);
    float cos = (float) Math.cos(plane.getPitchRad());
    float sin = (float) Math.sin(plane.getPitchRad());
    for (int v = 0; v < HEIGHT; v++) {
      for (int u = 0; u < WIDTH; u++) {
        int index = (v * WIDTH) + u;
        float bestZ = traceDepth(u, v, cos, sin);
        short millimeters = quantize(bestZ, random);
        depth[index] = millimeters;
        if (labelsOut != null) {
          labelsOut[index] = millimeters == 0 ? (byte) BodyPart.BACKGROUND : (byte) lastHitLabel;
        }
      }
    }
    return depth;
  }

  /** Traça um pixel e devolve o Z mais próximo, deixando o rótulo vencedor em {@link #lastHitLabel}. */
  private float traceDepth(int u, int v, float cos, float sin) {
    float rayX = (u - intrinsics.getCx()) / intrinsics.getFx();
    float rayY = (v - intrinsics.getCy()) / intrinsics.getFy();
    float norm = (float) Math.sqrt((rayX * rayX) + (rayY * rayY) + 1f);
    float dirX = rayX / norm;
    float dirY = rayY / norm;
    float dirZ = 1f / norm;

    lastHitLabel = BodyPart.BACKGROUND;
    float bestZ = sceneBackgroundZ(dirY, dirZ, cos, sin);
    for (int capsule = 0; capsule < tracer.size(); capsule++) {
      float hit = tracer.intersect(capsule, dirX, dirY, dirZ);
      if (hit <= 0f) {
        continue;
      }
      float candidateZ = hit * dirZ;
      if (candidateZ > 0f && candidateZ < bestZ) {
        bestZ = candidateZ;
        lastHitLabel = tracer.labelOf(capsule);
      }
    }
    return bestZ;
  }

  /** Z do piso ou da parede de fundo, o que o raio encontrar primeiro. */
  private float sceneBackgroundZ(float dirY, float dirZ, float cos, float sin) {
    float best = Float.MAX_VALUE;
    float floorDenominator = (dirY * cos) + (dirZ * sin);
    if (floorDenominator > 1e-5f) {
      float hit = plane.getCameraHeightM() / floorDenominator;
      best = Math.min(best, hit * dirZ);
    }
    float wallDenominator = (dirZ * cos) - (dirY * sin);
    if (wallDenominator > 1e-5f) {
      float hit = backWallDepthM / wallDenominator;
      best = Math.min(best, hit * dirZ);
    }
    return best;
  }

  /** Aplica ruído axial e dropout e converte para milímetros. */
  private short quantize(float depthMeters, Random random) {
    if (depthMeters <= 0f || depthMeters == Float.MAX_VALUE) {
      return 0;
    }
    if (random.nextFloat() < dropoutRatio) {
      return 0;
    }
    float sigma = noiseCoeff * depthMeters * depthMeters;
    float noisy = depthMeters + (float) (random.nextGaussian() * sigma);
    int millimeters = Math.round(noisy * 1000f);
    if (millimeters <= 0 || millimeters > 65000) {
      return 0;
    }
    return (short) millimeters;
  }
}
