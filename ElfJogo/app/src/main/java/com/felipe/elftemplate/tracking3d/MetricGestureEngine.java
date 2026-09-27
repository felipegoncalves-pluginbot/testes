package com.felipe.elftemplate.tracking3d;

import com.felipe.elftemplate.tracking.KinectTrackingEngine.GestureType;
import com.felipe.elftemplate.tracking.TrackingResult;

/**
 * Gestos e posturas medidos em metros, centímetros e metros por segundo.
 *
 * <p>Todo limiar aqui é invariante à distância, que era o defeito estrutural do classificador antigo:
 * "mão 0,04 acima do ombro" e "velocidade 2,2 por segundo" eram frações de quadro, então o mesmo
 * movimento disparava a 1 m e não disparava a 2,5 m. Aqui o critério é "mão 10 cm acima do ombro" e
 * "mão a 1,6 m/s", que valem em qualquer ponto da sala.
 *
 * <p>Jump e duck usam a altura do quadril acima do <b>piso</b> comparada a uma linha de base própria
 * do jogador, e não o deslocamento do centróide no quadro — que subia e descia quando a pessoa apenas
 * se aproximava da câmera.
 */
public final class MetricGestureEngine {

  /** Histerese de mão levantada, em metros acima do ombro. */
  private static final float RAISE_ENTER_M = 0.10f;

  private static final float RAISE_EXIT_M = 0.02f;

  /** Tolerância vertical para considerar o braço horizontal em T-pose, em metros. */
  private static final float TPOSE_VERTICAL_TOLERANCE_M = 0.13f;

  /** Fração do comprimento do braço que precisa estar projetada na horizontal para ser T-pose. */
  private static final float TPOSE_EXTENSION_RATIO = 0.72f;

  /** Histerese de salto e agachamento, em metros de variação da altura do quadril. */
  private static final float JUMP_ENTER_M = 0.07f;

  private static final float JUMP_EXIT_M = 0.04f;
  private static final float DUCK_ENTER_M = 0.14f;
  private static final float DUCK_EXIT_M = 0.09f;

  /** Velocidade lateral da mão que caracteriza um swipe, em m/s. */
  private static final float SWIPE_SPEED_MPS = 1.6f;

  /** Velocidade vertical que caracteriza uma raquetada de baixo para cima, em m/s. */
  private static final float SWING_SPEED_MPS = 1.8f;

  private static final long GESTURE_COOLDOWN_MS = 250L;

  /** Constante de adaptação da linha de base do quadril, por segundo. */
  private static final float BASELINE_RATE_PER_SEC = 0.35f;

  private boolean leftRaised;
  private boolean rightRaised;
  private boolean jumping;
  private boolean ducking;
  private float leftElevation;
  private float rightElevation;
  private float hipBaselineM;
  private boolean baselineReady;

  private float lastLeftWristX;
  private float lastRightWristX;
  private float lastLeftWristY;
  private float lastRightWristY;
  private long lastSampleMs;
  private long lastTriggerMs;
  private GestureType gesture = GestureType.IDLE;

  public void reset() {
    leftRaised = false;
    rightRaised = false;
    jumping = false;
    ducking = false;
    leftElevation = 0f;
    rightElevation = 0f;
    baselineReady = false;
    lastSampleMs = 0;
    lastTriggerMs = 0;
    gesture = GestureType.IDLE;
  }

  /** Atualiza todos os estados a partir do esqueleto métrico já filtrado. */
  public void update(MetricSkeleton skeleton) {
    if (skeleton == null || !skeleton.valid) {
      reset();
      return;
    }
    updateArmStates(skeleton);
    updateHipBaseline(skeleton);
    GestureType motion = detectMotionGesture(skeleton);
    gesture = classify(skeleton, motion);
  }

  private void updateArmStates(MetricSkeleton skeleton) {
    float armLength = Math.max(0.15f, BodyProportions.ARM_LENGTH * skeleton.statureM);
    updateArm(skeleton, true, armLength);
    updateArm(skeleton, false, armLength);
  }

  /**
   * Atualiza um braço somente quando o punho dele foi observado.
   *
   * <p>Sem esta guarda, um punho sintetizado por proporção — que fica em posição de repouso ao lado do
   * corpo — era lido como "braço abaixado" com a mesma autoridade de uma medição. No robô isso
   * aparecia no logcat como elevação alternando entre 0,00 e 1,00 em quadros consecutivos e a asa
   * indo e voltando sem ninguém mexer o braço. Quando não há medição, o estado anterior é mantido: é
   * melhor a asa ficar onde estava do que reagir a um valor inventado.
   */
  private void updateArm(MetricSkeleton skeleton, boolean anatomicalLeft, float armLength) {
    int wristJoint = anatomicalLeft ? MetricSkeleton.LEFT_WRIST : MetricSkeleton.RIGHT_WRIST;
    if (skeleton.confidence(wristJoint) < GeodesicSkeletonFitter.CONFIDENCE_USABLE) {
      return;
    }
    int shoulderJoint =
        anatomicalLeft ? MetricSkeleton.LEFT_SHOULDER : MetricSkeleton.RIGHT_SHOULDER;
    float delta = skeleton.y(wristJoint) - skeleton.y(shoulderJoint);
    if (anatomicalLeft) {
      leftRaised = updateRaise(leftRaised, delta);
      leftElevation = elevation(delta, armLength);
    } else {
      rightRaised = updateRaise(rightRaised, delta);
      rightElevation = elevation(delta, armLength);
    }
  }

  private static boolean updateRaise(boolean previous, float wristAboveShoulderM) {
    return previous
        ? wristAboveShoulderM > RAISE_EXIT_M
        : wristAboveShoulderM > RAISE_ENTER_M;
  }

  /**
   * Elevação normalizada do braço: 0 = punho na base do arco, 1 = punho no topo.
   *
   * <p>Vem da razão entre o deslocamento vertical real punho-ombro e o comprimento do braço, então é
   * o seno do ângulo do braço remapeado — uma grandeza geométrica, não uma fração de quadro.
   */
  private static float elevation(float delta, float armLength) {
    float ratio = (delta / armLength + 1f) * 0.5f;
    if (ratio < 0f) {
      return 0f;
    }
    return ratio > 1f ? 1f : ratio;
  }

  /** Linha de base da altura do quadril, adaptada devagar e congelada durante salto/agachamento. */
  private void updateHipBaseline(MetricSkeleton skeleton) {
    float hipHeight =
        (skeleton.y(MetricSkeleton.LEFT_HIP) + skeleton.y(MetricSkeleton.RIGHT_HIP)) * 0.5f;
    if (!baselineReady) {
      hipBaselineM = hipHeight;
      baselineReady = true;
    }
    float delta = hipHeight - hipBaselineM;
    jumping = jumping ? delta > JUMP_EXIT_M : delta > JUMP_ENTER_M;
    ducking = ducking ? delta < -DUCK_EXIT_M : delta < -DUCK_ENTER_M;
    if (!jumping && !ducking) {
      float step = adaptationStep(skeleton.timestampMs);
      hipBaselineM = hipBaselineM + (step * (hipHeight - hipBaselineM));
    }
  }

  private float adaptationStep(long timestampMs) {
    if (lastSampleMs <= 0 || timestampMs <= lastSampleMs) {
      return BASELINE_RATE_PER_SEC * 0.033f;
    }
    float deltaSeconds = (timestampMs - lastSampleMs) / 1000f;
    float step = BASELINE_RATE_PER_SEC * Math.min(0.2f, deltaSeconds);
    return step;
  }

  /**
   * Swipes e raquetada, com velocidade real da mão em metros por segundo.
   *
   * <p>Só amostra velocidade com os dois punhos observados. Quando a medição volta depois de uma
   * ausência, ou quando a tolerância de continuidade expira e o braço assume a pose de repouso, a
   * posição do punho muda muito de um quadro para o outro sem que a mão tenha se movido. Derivar
   * velocidade disso produziria uma raquetada ou um aceno que ninguém fez.
   */
  private GestureType detectMotionGesture(MetricSkeleton skeleton) {
    long now = skeleton.timestampMs;
    GestureType detected = GestureType.IDLE;
    if (!bothWristsMeasured(skeleton)) {
      lastSampleMs = 0;
      return detected;
    }
    float leftX = skeleton.x(MetricSkeleton.LEFT_WRIST);
    float rightX = skeleton.x(MetricSkeleton.RIGHT_WRIST);
    float leftY = skeleton.y(MetricSkeleton.LEFT_WRIST);
    float rightY = skeleton.y(MetricSkeleton.RIGHT_WRIST);
    float deltaSeconds = lastSampleMs > 0 ? (now - lastSampleMs) / 1000f : 0f;
    if (deltaSeconds > 0.012f && deltaSeconds < 0.30f && now - lastTriggerMs > GESTURE_COOLDOWN_MS) {
      detected = fastestGesture(leftX, rightX, leftY, rightY, deltaSeconds);
      if (detected != GestureType.IDLE) {
        lastTriggerMs = now;
      }
    }
    lastLeftWristX = leftX;
    lastRightWristX = rightX;
    lastLeftWristY = leftY;
    lastRightWristY = rightY;
    lastSampleMs = now;
    return detected;
  }

  private static boolean bothWristsMeasured(MetricSkeleton skeleton) {
    return skeleton.confidence(MetricSkeleton.LEFT_WRIST) >= GeodesicSkeletonFitter.CONFIDENCE_USABLE
        && skeleton.confidence(MetricSkeleton.RIGHT_WRIST)
            >= GeodesicSkeletonFitter.CONFIDENCE_USABLE;
  }

  private GestureType fastestGesture(
      float leftX, float rightX, float leftY, float rightY, float deltaSeconds) {
    float leftVx = (leftX - lastLeftWristX) / deltaSeconds;
    float rightVx = (rightX - lastRightWristX) / deltaSeconds;
    float leftVy = (leftY - lastLeftWristY) / deltaSeconds;
    float rightVy = (rightY - lastRightWristY) / deltaSeconds;
    if (leftVy > SWING_SPEED_MPS || rightVy > SWING_SPEED_MPS) {
      return GestureType.SWING_UP;
    }
    // X cresce para a direita da câmera, que é a esquerda de quem joga.
    if (leftVx > SWIPE_SPEED_MPS || rightVx > SWIPE_SPEED_MPS) {
      return GestureType.SWIPE_RIGHT;
    }
    if (leftVx < -SWIPE_SPEED_MPS || rightVx < -SWIPE_SPEED_MPS) {
      return GestureType.SWIPE_LEFT;
    }
    return GestureType.IDLE;
  }

  private GestureType classify(MetricSkeleton skeleton, GestureType motion) {
    if (jumping) {
      return GestureType.JUMP;
    }
    if (ducking) {
      return GestureType.DUCK;
    }
    if (isTpose(skeleton)) {
      return GestureType.T_POSE;
    }
    if (motion != GestureType.IDLE) {
      return motion;
    }
    if (leftRaised && rightRaised) {
      return GestureType.HANDS_UP;
    }
    if (leftRaised) {
      // Mão esquerda da pessoa aparece à direita da imagem: contrato antigo chama de RIGHT_HAND_UP.
      return GestureType.RIGHT_HAND_UP;
    }
    return rightRaised ? GestureType.LEFT_HAND_UP : GestureType.IDLE;
  }

  /** T-pose por geometria 3D: braços horizontais e efetivamente estendidos para os lados. */
  private static boolean isTpose(MetricSkeleton skeleton) {
    float armLength = Math.max(0.15f, BodyProportions.ARM_LENGTH * skeleton.statureM);
    float minExtension = armLength * TPOSE_EXTENSION_RATIO;
    return isArmHorizontal(skeleton, MetricSkeleton.LEFT_WRIST, MetricSkeleton.LEFT_SHOULDER, minExtension)
        && isArmHorizontal(
            skeleton, MetricSkeleton.RIGHT_WRIST, MetricSkeleton.RIGHT_SHOULDER, minExtension);
  }

  private static boolean isArmHorizontal(
      MetricSkeleton skeleton, int wristJoint, int shoulderJoint, float minExtension) {
    float verticalOffset =
        Math.abs(skeleton.y(wristJoint) - skeleton.y(shoulderJoint));
    float lateralReach = Math.abs(skeleton.x(wristJoint) - skeleton.x(shoulderJoint));
    return verticalOffset < TPOSE_VERTICAL_TOLERANCE_M && lateralReach > minExtension;
  }

  /** Escreve gestos e elevações no contrato antigo, aplicando a troca anatômica de lados. */
  public void applyTo(TrackingResult out) {
    out.activeGesture = gesture;
    out.isJumping = jumping;
    out.isDucking = ducking;
    out.isLeftHandRaised = rightRaised;
    out.isRightHandRaised = leftRaised;
    out.leftHandElevation = rightElevation;
    out.rightHandElevation = leftElevation;
  }

  public GestureType getGesture() {
    return gesture;
  }

  public boolean isAnatomicalLeftRaised() {
    return leftRaised;
  }

  public boolean isAnatomicalRightRaised() {
    return rightRaised;
  }

  /** Altura de referência do quadril, em metros: base de comparação de salto e agachamento. */
  public float getHipBaselineM() {
    return hipBaselineM;
  }
}
