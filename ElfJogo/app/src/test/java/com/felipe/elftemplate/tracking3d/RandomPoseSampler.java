package com.felipe.elftemplate.tracking3d;

import java.util.Random;

/**
 * Sorteia cena e pose plausíveis para o treino do classificador de partes.
 *
 * <p>Cada faixa aqui é uma decisão sobre o que o classificador vai suportar em operação, e é o lugar
 * onde as preocupações de mundo real entram no sistema: estatura de criança a adulto alto, o robô nem
 * sempre perfeitamente nivelado, a pessoa nem sempre de frente, braço em qualquer elevação, agachar,
 * saltar e inclinar o tronco. O que não estiver amostrado aqui o classificador não vai ter visto, e
 * isso é explícito por desenho.
 *
 * <p>A variação de iluminação não aparece porque não existe: profundidade por luz estruturada
 * infravermelha não depende da luz ambiente da sala. O que varia no lugar dela é o ruído axial e o
 * dropout do sensor, que são o análogo físico correto.
 */
final class RandomPoseSampler {

  private static final float MIN_STATURE_M = 1.25f;
  private static final float MAX_STATURE_M = 1.95f;

  /** Faixa útil no robô: mais perto que 0,9 m o Astra perde, mais longe que 3,8 m sobra pouco corpo. */
  private static final float MIN_DEPTH_M = 0.95f;

  private static final float MAX_DEPTH_M = 3.80f;
  private static final float MAX_LATERAL_M = 0.55f;
  private static final float MAX_YAW_DEG = 40f;

  /** Faixas de altura e inclinação do sensor sorteadas em cada quadro. */
  static final class Mount {
    final float minHeightM;
    final float maxHeightM;
    final float minPitchDeg;
    final float maxPitchDeg;

    Mount(float minHeightM, float maxHeightM, float minPitchDeg, float maxPitchDeg) {
      this.minHeightM = minHeightM;
      this.maxHeightM = maxHeightM;
      this.minPitchDeg = minPitchDeg;
      this.maxPitchDeg = maxPitchDeg;
    }
  }

  /**
   * Astra na cabeça do Sanbot Elf (robô de 0,90 m): de 0,62 a 0,92 m, de 12° para cima a 20° para
   * baixo (pitch positivo aponta para baixo, como em {@link GroundPlane}). Cobre a posição do
   * sensor dentro da cabeça e a faixa de inclinação da cabeça.
   */
  static final Mount ROBOT_HEAD = new Mount(0.62f, 0.92f, -12f, 20f);

  /** Montagem que o modelo antigo usava (sensor imaginado no peito a ~1 m); só para comparação. */
  static final Mount LEGACY_TORSO = new Mount(0.92f, 1.18f, 0f, 13f);

  private final Mount mount;

  RandomPoseSampler() {
    this(ROBOT_HEAD);
  }

  RandomPoseSampler(Mount mount) {
    this.mount = mount;
  }

  Mount getMount() {
    return mount;
  }

  /** Quadril de 0,78 a 1,02 da altura nominal cobre de agachamento profundo a joelho travado. */
  private static final float MIN_HIP_RATIO = 0.78f;

  private static final float MAX_HIP_RATIO = 1.02f;
  private static final float MAX_JUMP_M = 0.14f;
  private static final float MIN_TORSO_LEAN_DEG = -10f;
  private static final float MAX_TORSO_LEAN_DEG = 22f;

  /** -92° é braço pendurado; +100° passa um pouco da vertical, como num alongamento. */
  private static final float MIN_ARM_ELEVATION_DEG = -92f;

  private static final float MAX_ARM_ELEVATION_DEG = 100f;
  private static final float MIN_ARM_AZIMUTH_DEG = -40f;
  private static final float MAX_ARM_AZIMUTH_DEG = 85f;
  private static final float MAX_ELBOW_FLEX_DEG = 135f;

  private static final float MIN_HIP_FLEX_DEG = -12f;
  private static final float MAX_HIP_FLEX_DEG = 50f;
  private static final float MAX_HIP_ABDUCT_DEG = 22f;
  private static final float MAX_KNEE_FLEX_DEG = 75f;

  /** Faixa de ruído axial do Astra e de dropout, para o modelo não depender de sensor perfeito. */
  private static final float MIN_NOISE_COEFF = 0.0020f;

  private static final float MAX_NOISE_COEFF = 0.0045f;
  private static final float MIN_DROPOUT = 0.01f;
  private static final float MAX_DROPOUT = 0.06f;

  /**
   * Configura câmera e cena no renderizador e monta um corpo aleatório.
   *
   * <p>Limpa o tracer antes de emitir: sem isso as cápsulas do quadro anterior continuariam na cena e
   * o rótulo passaria a discordar da imagem.
   */
  void apply(PosedHumanSkeleton body, SyntheticDepthRenderer renderer, Random random) {
    float depth = range(random, MIN_DEPTH_M, MAX_DEPTH_M);
    renderer.setCamera(
        range(random, mount.minHeightM, mount.maxHeightM),
        range(random, mount.minPitchDeg, mount.maxPitchDeg));
    renderer.setBackWallDepthM(depth + range(random, 0.35f, 2.2f));
    renderer.setNoise(
        range(random, MIN_NOISE_COEFF, MAX_NOISE_COEFF), range(random, MIN_DROPOUT, MAX_DROPOUT));
    renderer.getTracer().clear();

    body.setBody(
        range(random, MIN_STATURE_M, MAX_STATURE_M),
        range(random, MIN_HIP_RATIO, MAX_HIP_RATIO),
        radians(range(random, MIN_TORSO_LEAN_DEG, MAX_TORSO_LEAN_DEG)),
        random.nextFloat() < 0.15f ? range(random, 0f, MAX_JUMP_M) : 0f);
    sampleArm(body, true, random);
    sampleArm(body, false, random);
    sampleLeg(body, true, random);
    sampleLeg(body, false, random);
    body.build(depth, range(random, -MAX_LATERAL_M, MAX_LATERAL_M), radians(yaw(random)));
  }

  /**
   * Yaw concentrado perto de zero.
   *
   * <p>O jogador olha para o robô na maior parte do tempo, então gastar metade das amostras em corpo
   * de perfil desperdiçaria capacidade da floresta. Elevar ao cubo um sorteio uniforme em [-1,1]
   * mantém a cauda sem inflar o centro.
   */
  private static float yaw(Random random) {
    float uniform = (random.nextFloat() * 2f) - 1f;
    return uniform * uniform * uniform * MAX_YAW_DEG;
  }

  private void sampleArm(PosedHumanSkeleton body, boolean anatomicalLeft, Random random) {
    body.setArm(
        anatomicalLeft,
        radians(range(random, MIN_ARM_ELEVATION_DEG, MAX_ARM_ELEVATION_DEG)),
        radians(range(random, MIN_ARM_AZIMUTH_DEG, MAX_ARM_AZIMUTH_DEG)),
        radians(range(random, 0f, MAX_ELBOW_FLEX_DEG)));
  }

  private void sampleLeg(PosedHumanSkeleton body, boolean anatomicalLeft, Random random) {
    body.setLeg(
        anatomicalLeft,
        radians(range(random, MIN_HIP_FLEX_DEG, MAX_HIP_FLEX_DEG)),
        radians(range(random, 0f, MAX_HIP_ABDUCT_DEG)),
        radians(range(random, 0f, MAX_KNEE_FLEX_DEG)));
  }

  private static float range(Random random, float min, float max) {
    return min + (random.nextFloat() * (max - min));
  }

  private static float radians(float degrees) {
    return (float) Math.toRadians(degrees);
  }
}
