package com.felipe.elftemplate.movement;

import android.util.Log;
import com.felipe.elftemplate.tracking.PlayerTracker;
import com.sanbot.opensdk.function.beans.headmotion.RelativeAngleHeadMotion;
import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;
import com.sanbot.opensdk.function.beans.wheelmotion.RelativeAngleWheelMotion;
import com.sanbot.opensdk.function.unit.HeadMotionManager;
import com.sanbot.opensdk.function.unit.WheelMotionManager;

/**
 * Controlador de Movimento de Elite (V10). Implementa "Head-Lead Trajectory": A cabeça trava no
 * alvo instantaneamente e o corpo segue a trajetória da cabeça de forma fluida.
 */
public class FollowMovementController {

  private static final String TAG = "FollowMoveCtrl";

  private enum MoveState {
    IDLE,
    FORWARD,
    CURVE_LEFT,
    CURVE_RIGHT,
    STOPPED
  }

  private final WheelMotionManager wheelManager;
  private final HeadMotionManager headManager;
  private final PlayerTracker playerTracker;

  private static final float X_CENTER = 0.5f;
  private static final float X_DEADZONE_HEAD = 0.05f; // Cabeça é ultra precisa
  private static final float X_DEADZONE_BODY = 0.15f; // Corpo é mais estável

  private static final float DISTANCE_TARGET = 1.0f; // 1.0 metro de distância alvo
  private static final float DISTANCE_DEADZONE = 0.2f;

  private static final int SPEED_BODY_MAX = 10;
  private static final int SPEED_BODY_MIN = 4; // Aumentado de 3
  private static final int SPEED_BODY_CURVE = 6; // Aumentado de 5

  private static final long COMMAND_REFRESH_MS =
      150; // Aumentado para 150ms para evitar saturação de comandos

  private MoveState currentState = MoveState.IDLE;
  private long lastCommandTime = 0;
  private long lastUpdateTime = 0;
  private int estimatedHeadOffset = 0;

  public FollowMovementController(WheelMotionManager wheelManager, HeadMotionManager headManager) {
    this.wheelManager = wheelManager;
    this.headManager = headManager;
    this.playerTracker = new PlayerTracker();
  }

  public void updateTarget(float x, float distanceMeters) {
    playerTracker.update(x, distanceMeters);
    lastUpdateTime = System.currentTimeMillis();

    // 1. A CABEÇA TRAVA NO ALVO (Resposta instantânea)
    processHeadLock();

    // 2. O CORPO SEGUE A TRAJETÓRIA
    processBodyFollow();
  }

  /** Mantém a cabeça sempre apontada para o usuário. */
  private void processHeadLock() {
    float x = playerTracker.getFilteredX();
    float xError = x - X_CENTER;

    if (Math.abs(xError) > X_DEADZONE_HEAD) {
      int headAction =
          xError < 0 ? RelativeAngleHeadMotion.ACTION_LEFT : RelativeAngleHeadMotion.ACTION_RIGHT;
      // Aumentando ganho para 60 para ser ultra agressivo na cabeça
      int angle = (int) (Math.abs(xError) * 60);

      if (angle > 0) {
        Log.i(TAG, "HEAD: xErr=" + String.format("%.2f", xError) + " angle=" + angle);
        headManager.doRelativeAngleMotion(
            new RelativeAngleHeadMotion((byte) headAction, (byte) angle));

        estimatedHeadOffset += (xError < 0 ? -angle : angle);
      }
    }
  }

  private void processBodyFollow() {
    long now = System.currentTimeMillis();
    if (now - lastCommandTime < COMMAND_REFRESH_MS) {
      return;
    }

    float distance = playerTracker.getFilteredWidth();
    float distanceError = distance - DISTANCE_TARGET;

    MoveState nextState;
    int nextSpeed;

    // Limites de offset para girar o corpo
    int turnThreshold = 8; // Reduzido de 12 para 8

    if (distanceError > DISTANCE_DEADZONE) {
      if (estimatedHeadOffset < -turnThreshold) {
        nextState = MoveState.CURVE_LEFT;
        nextSpeed = SPEED_BODY_CURVE;
      } else if (estimatedHeadOffset > turnThreshold) {
        nextState = MoveState.CURVE_RIGHT;
        nextSpeed = SPEED_BODY_CURVE;
      } else {
        nextState = MoveState.FORWARD;
        nextSpeed =
            (int)
                (SPEED_BODY_MIN
                    + (Math.min(distanceError, 1.0f) * (SPEED_BODY_MAX - SPEED_BODY_MIN)));
      }
    } else {
      nextState = MoveState.IDLE;
      nextSpeed = 0;
    }

    if (nextState != currentState || nextSpeed > 0 || now - lastCommandTime > 800) {
      Log.i(
          TAG,
          "BODY: dist="
              + String.format("%.2f", distance)
              + " offset="
              + estimatedHeadOffset
              + " state="
              + nextState
              + " speed="
              + nextSpeed);
      applyState(nextState, nextSpeed);
    }

    if (nextState == MoveState.CURVE_LEFT || nextState == MoveState.CURVE_RIGHT) {
      estimatedHeadOffset *= 0.5; // Damping mais forte (0.6 -> 0.5)
    } else {
      estimatedHeadOffset *= 0.8; // Gradualmente centraliza
    }
  }

  private void applyState(MoveState state, int speed) {
    if (wheelManager == null) return;
    currentState = state;
    lastCommandTime = System.currentTimeMillis();

    switch (state) {
      case FORWARD:
        executeBurst(NoAngleWheelMotion.ACTION_FORWARD, speed);
        break;
      case CURVE_LEFT:
        // Voltar para TURN_LEFT que é mais padrão/robusto no Sanbot
        executeBurst(NoAngleWheelMotion.ACTION_TURN_LEFT, speed);
        break;
      case CURVE_RIGHT:
        // Voltar para TURN_RIGHT que é mais padrão/robusto no Sanbot
        executeBurst(NoAngleWheelMotion.ACTION_TURN_RIGHT, speed);
        break;
      case IDLE:
      case STOPPED:
        forceStopHardware();
        break;
    }
  }

  private void executeBurst(int action, int speed) {
    NoAngleWheelMotion motion = new NoAngleWheelMotion((byte) action, (byte) speed, 400);
    wheelManager.doNoAngleMotion(motion);
  }

  public void checkTargetStatus() {
    long now = System.currentTimeMillis();
    if (now - lastUpdateTime > 1000 && currentState != MoveState.STOPPED) {
      stopRobot();
    }
  }

  public void stopRobot() {
    currentState = MoveState.STOPPED;
    estimatedHeadOffset = 0;
    forceStopHardware();
  }

  private void forceStopHardware() {
    if (wheelManager == null) return;
    wheelManager.doRelativeAngleMotion(
        new RelativeAngleWheelMotion(
            (byte) RelativeAngleWheelMotion.TURN_STOP, (byte) 5, (byte) 0));

    try {
      headManager.doAbsoluteAngleMotion(
          new com.sanbot.opensdk.function.beans.headmotion.AbsoluteAngleHeadMotion(
              com.sanbot.opensdk.function.beans.headmotion.AbsoluteAngleHeadMotion
                  .ACTION_HORIZONTAL,
              90));
    } catch (Exception ignored) {
    }
  }
}
