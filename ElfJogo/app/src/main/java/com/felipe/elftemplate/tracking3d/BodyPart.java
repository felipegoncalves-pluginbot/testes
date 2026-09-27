package com.felipe.elftemplate.tracking3d;

/**
 * Taxonomia de partes do corpo para classificação por pixel.
 *
 * <p>É a peça central da abordagem que o Kinect do Xbox 360 usa de verdade: em vez de procurar
 * geometricamente onde está a mão, treina-se um classificador para responder, <em>para cada pixel de
 * profundidade</em>, a que parte do corpo aquele pixel pertence. As juntas saem depois, como os modos
 * das nuvens de pixels de cada parte. A referência é Shotton et al., <i>Real-Time Human Pose
 * Recognition in Parts from Single Depth Images</i>, CVPR 2011.
 *
 * <p><b>Por que existem partes que não geram junta nenhuma.</b> Braço, antebraço, coxa e perna estão
 * na lista mesmo sem virar junta. Sem elas, um pixel no meio do antebraço seria obrigado a escolher
 * entre "cotovelo" e "mão", e a nuvem de cada uma dessas classes ficaria esticada ao longo do membro
 * — o modo sairia do lugar. Com as partes intermediárias, cada classe de junta fica compacta em volta
 * da articulação, que é a condição para o mean shift acertar. O artigo original usa 31 partes pela
 * mesma razão.
 *
 * <p>Lateralidade é anatômica, igual a {@link MetricSkeleton}: {@code LEFT_*} é o lado esquerdo da
 * pessoa, que aparece com X maior na imagem porque ela está de frente para a câmera.
 *
 * <p>Índices são constantes {@code int}, não enum, para servirem de índice de array em laço quente
 * sem boxing e sem {@code values()} alocando cópia.
 */
public final class BodyPart {

  /** Pixel que não pertence a nenhuma pessoa. Toda folha da floresta tem massa nesta classe. */
  public static final int BACKGROUND = 0;

  public static final int HEAD = 1;
  public static final int NECK = 2;
  public static final int CHEST = 3;
  public static final int BELLY = 4;

  public static final int LEFT_SHOULDER = 5;
  public static final int LEFT_UPPER_ARM = 6;
  public static final int LEFT_ELBOW = 7;
  public static final int LEFT_FOREARM = 8;
  public static final int LEFT_HAND = 9;
  public static final int LEFT_HIP = 10;
  public static final int LEFT_THIGH = 11;
  public static final int LEFT_KNEE = 12;
  public static final int LEFT_SHANK = 13;
  public static final int LEFT_FOOT = 14;

  public static final int RIGHT_SHOULDER = 15;
  public static final int RIGHT_UPPER_ARM = 16;
  public static final int RIGHT_ELBOW = 17;
  public static final int RIGHT_FOREARM = 18;
  public static final int RIGHT_HAND = 19;
  public static final int RIGHT_HIP = 20;
  public static final int RIGHT_THIGH = 21;
  public static final int RIGHT_KNEE = 22;
  public static final int RIGHT_SHANK = 23;
  public static final int RIGHT_FOOT = 24;

  public static final int COUNT = 25;

  /** Sentinela de "esta parte não vira junta". */
  public static final int NO_JOINT = -1;

  /**
   * Junta que cada parte propõe, indexada por parte.
   *
   * <p>Tabela em array, não {@code switch}: o proponente de juntas percorre isto uma vez por parte a
   * cada frame, e um array é previsível.
   */
  private static final int[] JOINT_OF_PART = new int[COUNT];

  /**
   * Distância da superfície visível até o centro articular, em metros, por parte.
   *
   * <p>O classificador só vê a casca do corpo: a nuvem de uma parte fica na face da frente do membro,
   * então o modo do mean shift cai deslocado para perto da câmera. Empurrar o modo para dentro pelo
   * raio aproximado do segmento coloca a junta onde ela está de fato. Shotton faz o mesmo com um
   * deslocamento aprendido por parte; aqui o valor vem da antropometria, que é medível e dispensa
   * treinar mais um parâmetro.
   */
  private static final float[] SURFACE_TO_CENTER_M = new float[COUNT];

  /** Nome curto para telemetria e mensagem de teste. */
  private static final String[] NAMES = new String[COUNT];

  static {
    for (int part = 0; part < COUNT; part++) {
      JOINT_OF_PART[part] = NO_JOINT;
    }
    mapTorso();
    mapArm(true);
    mapArm(false);
    mapLeg(true);
    mapLeg(false);
    fillNames();
  }

  private BodyPart() {}

  private static void mapTorso() {
    JOINT_OF_PART[HEAD] = MetricSkeleton.HEAD;
    JOINT_OF_PART[NECK] = MetricSkeleton.NECK;
    JOINT_OF_PART[BELLY] = MetricSkeleton.SPINE;
    SURFACE_TO_CENTER_M[HEAD] = 0.090f;
    SURFACE_TO_CENTER_M[NECK] = 0.055f;
    SURFACE_TO_CENTER_M[CHEST] = 0.100f;
    SURFACE_TO_CENTER_M[BELLY] = 0.100f;
  }

  private static void mapArm(boolean anatomicalLeft) {
    int shoulder = anatomicalLeft ? LEFT_SHOULDER : RIGHT_SHOULDER;
    int upperArm = anatomicalLeft ? LEFT_UPPER_ARM : RIGHT_UPPER_ARM;
    int elbow = anatomicalLeft ? LEFT_ELBOW : RIGHT_ELBOW;
    int forearm = anatomicalLeft ? LEFT_FOREARM : RIGHT_FOREARM;
    int hand = anatomicalLeft ? LEFT_HAND : RIGHT_HAND;
    JOINT_OF_PART[shoulder] =
        anatomicalLeft ? MetricSkeleton.LEFT_SHOULDER : MetricSkeleton.RIGHT_SHOULDER;
    JOINT_OF_PART[elbow] = anatomicalLeft ? MetricSkeleton.LEFT_ELBOW : MetricSkeleton.RIGHT_ELBOW;
    JOINT_OF_PART[hand] = anatomicalLeft ? MetricSkeleton.LEFT_WRIST : MetricSkeleton.RIGHT_WRIST;
    SURFACE_TO_CENTER_M[shoulder] = 0.070f;
    SURFACE_TO_CENTER_M[upperArm] = 0.055f;
    SURFACE_TO_CENTER_M[elbow] = 0.067f;
    SURFACE_TO_CENTER_M[forearm] = 0.045f;
    SURFACE_TO_CENTER_M[hand] = 0.057f;
  }

  private static void mapLeg(boolean anatomicalLeft) {
    int hip = anatomicalLeft ? LEFT_HIP : RIGHT_HIP;
    int thigh = anatomicalLeft ? LEFT_THIGH : RIGHT_THIGH;
    int knee = anatomicalLeft ? LEFT_KNEE : RIGHT_KNEE;
    int shank = anatomicalLeft ? LEFT_SHANK : RIGHT_SHANK;
    int foot = anatomicalLeft ? LEFT_FOOT : RIGHT_FOOT;
    JOINT_OF_PART[hip] = anatomicalLeft ? MetricSkeleton.LEFT_HIP : MetricSkeleton.RIGHT_HIP;
    JOINT_OF_PART[knee] = anatomicalLeft ? MetricSkeleton.LEFT_KNEE : MetricSkeleton.RIGHT_KNEE;
    JOINT_OF_PART[foot] = anatomicalLeft ? MetricSkeleton.LEFT_ANKLE : MetricSkeleton.RIGHT_ANKLE;
    SURFACE_TO_CENTER_M[hip] = 0.090f;
    SURFACE_TO_CENTER_M[thigh] = 0.080f;
    SURFACE_TO_CENTER_M[knee] = 0.092f;
    SURFACE_TO_CENTER_M[shank] = 0.055f;
    SURFACE_TO_CENTER_M[foot] = 0.067f;
  }

  private static void fillNames() {
    NAMES[BACKGROUND] = "fundo";
    NAMES[HEAD] = "cabeca";
    NAMES[NECK] = "pescoco";
    NAMES[CHEST] = "torax";
    NAMES[BELLY] = "abdomen";
    nameSide(true);
    nameSide(false);
  }

  private static void nameSide(boolean anatomicalLeft) {
    String suffix = anatomicalLeft ? "_E" : "_D";
    int base = anatomicalLeft ? LEFT_SHOULDER : RIGHT_SHOULDER;
    NAMES[base] = "ombro" + suffix;
    NAMES[base + 1] = "braco" + suffix;
    NAMES[base + 2] = "cotovelo" + suffix;
    NAMES[base + 3] = "antebraco" + suffix;
    NAMES[base + 4] = "mao" + suffix;
    NAMES[base + 5] = "quadril" + suffix;
    NAMES[base + 6] = "coxa" + suffix;
    NAMES[base + 7] = "joelho" + suffix;
    NAMES[base + 8] = "perna" + suffix;
    NAMES[base + 9] = "pe" + suffix;
  }

  /** Junta que esta parte propõe, ou {@link #NO_JOINT} para partes puramente de contexto. */
  public static int jointOf(int part) {
    if (part < 0 || part >= COUNT) {
      return NO_JOINT;
    }
    return JOINT_OF_PART[part];
  }

  /** Deslocamento da casca visível até o centro articular, em metros. */
  public static float surfaceToCenterM(int part) {
    if (part < 0 || part >= COUNT) {
      return 0f;
    }
    return SURFACE_TO_CENTER_M[part];
  }

  public static String name(int part) {
    if (part < 0 || part >= COUNT) {
      return "invalido";
    }
    return NAMES[part];
  }

  /** True para as partes que ficam no corpo de uma pessoa (tudo menos o fundo). */
  public static boolean isBody(int part) {
    return part > BACKGROUND && part < COUNT;
  }
}
