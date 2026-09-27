package com.felipe.elftemplate.movement;

import com.sanbot.opensdk.function.beans.headmotion.AbsoluteAngleHeadMotion;
import com.sanbot.opensdk.function.beans.wheelmotion.RelativeAngleWheelMotion;
import com.sanbot.opensdk.function.beans.wing.NoAngleWingMotion;
import com.sanbot.opensdk.function.unit.HeadMotionManager;
import com.sanbot.opensdk.function.unit.WheelMotionManager;
import com.sanbot.opensdk.function.unit.WingMotionManager;

/** Encapsula o controle físico do Sanbot para o jogo. */
public class RobotGameMovementHandler {
  private WheelMotionManager wheelManager;
  private WingMotionManager wingManager;
  private HeadMotionManager headManager;

  private int lastHorizontalAngle = 90;
  private static final int ANGLE_THRESHOLD = 5; // Só move se a diferença for > 5 graus

  public RobotGameMovementHandler(
      WheelMotionManager wheel, WingMotionManager wing, HeadMotionManager head) {
    this.wheelManager = wheel;
    this.wingManager = wing;
    this.headManager = head;
  }

  /**
   * Faz a cabeça do robô seguir a posição X da bola.
   *
   * @param ballX Posição entre 0 e 1.
   */
  public void trackBallWithHead(float ballX) {
    // Mapeia 0..1 para 30..150 graus (centro em 90)
    int horizontalAngle = (int) (30 + (ballX * 120));

    if (Math.abs(horizontalAngle - lastHorizontalAngle) >= ANGLE_THRESHOLD) {
      headManager.doAbsoluteAngleMotion(
          new AbsoluteAngleHeadMotion(AbsoluteAngleHeadMotion.ACTION_HORIZONTAL, horizontalAngle));
      lastHorizontalAngle = horizontalAngle;
    }
  }

  /** Realiza o movimento de batida com as asas. */
  public void performHitSwing() {
    wingManager.doNoAngleMotion(
        new NoAngleWingMotion(NoAngleWingMotion.PART_BOTH, (byte) 10, NoAngleWingMotion.ACTION_UP));
    // Reset após um curto delay (gerenciado externamente ou por callback)
  }

  public void stopAll() {
    wheelManager.doRelativeAngleMotion(
        new RelativeAngleWheelMotion(RelativeAngleWheelMotion.TURN_STOP, 0, 0));
  }
}
