package com.felipe.elftemplate.movement;

import com.sanbot.opensdk.function.beans.wing.AbsoluteAngleWingMotion;
import com.sanbot.opensdk.function.beans.wing.NoAngleWingMotion;
import com.sanbot.opensdk.function.unit.WingMotionManager;

/**
 * Controlador de movimentação e estados das asas (braços) do Sanbot Elf.
 * Aplica debounce de tempo (350ms) e controle proporcional de ângulo absoluto (updateMirrorAngles)
 * com deadband para impedir ruídos mecânicos e vibração nos servos.
 */
public class RobotWingController {

  public static final long MIRROR_UPDATE_MIN_MS = 150L;
  public static final int MIRROR_ANGLE_DEADBAND = 4;
  private static final long WING_DEBOUNCE_MS = 350L;

  private final WingMotionManager wingManager;
  private boolean currentLeftWingUp = false;
  private boolean currentRightWingUp = false;
  private long lastLeftWingUpdateTime = 0;
  private long lastRightWingUpdateTime = 0;

  private int lastLeftMirrorAngle = 0;
  private int lastRightMirrorAngle = 0;
  private long lastMirrorUpdateMs = 0L;
  private long lastMirrorResetMs = 0L;
  private static final long MIRROR_RESET_RESYNC_MS = 2000L;

  public RobotWingController(WingMotionManager wingManager) {
    this.wingManager = wingManager;
  }

  public void updateWings(boolean leftWingUp, boolean rightWingUp) {
    long now = System.currentTimeMillis();

    if (leftWingUp != currentLeftWingUp && (now - lastLeftWingUpdateTime >= WING_DEBOUNCE_MS)) {
      currentLeftWingUp = leftWingUp;
      lastLeftWingUpdateTime = now;
      if (wingManager != null) {
        byte action = leftWingUp ? NoAngleWingMotion.ACTION_UP : NoAngleWingMotion.ACTION_DOWN;
        wingManager.doNoAngleMotion(new NoAngleWingMotion(NoAngleWingMotion.PART_LEFT, 5, action));
      }
    }

    if (rightWingUp != currentRightWingUp && (now - lastRightWingUpdateTime >= WING_DEBOUNCE_MS)) {
      currentRightWingUp = rightWingUp;
      lastRightWingUpdateTime = now;
      if (wingManager != null) {
        byte action = rightWingUp ? NoAngleWingMotion.ACTION_UP : NoAngleWingMotion.ACTION_DOWN;
        wingManager.doNoAngleMotion(new NoAngleWingMotion(NoAngleWingMotion.PART_RIGHT, 5, action));
      }
    }
  }

  public void updateMirrorAngles(int targetLeftAngle, int targetRightAngle) {
    long now = System.currentTimeMillis();
    if (now - lastMirrorUpdateMs < MIRROR_UPDATE_MIN_MS) {
      return;
    }

    boolean leftChanged = Math.abs(targetLeftAngle - lastLeftMirrorAngle) >= MIRROR_ANGLE_DEADBAND;
    boolean rightChanged = Math.abs(targetRightAngle - lastRightMirrorAngle) >= MIRROR_ANGLE_DEADBAND;

    if (!leftChanged && !rightChanged) {
      return;
    }

    lastMirrorUpdateMs = now;
    if (wingManager != null) {
      boolean needResync =
          targetLeftAngle <= 0
              && targetRightAngle <= 0
              && now - lastMirrorResetMs >= MIRROR_RESET_RESYNC_MS;
      if (leftChanged) {
        lastLeftMirrorAngle = targetLeftAngle;
        if (targetLeftAngle <= 0) {
          wingManager.doNoAngleMotion(
              new NoAngleWingMotion(NoAngleWingMotion.PART_LEFT, 5, NoAngleWingMotion.ACTION_RESET));
          lastMirrorResetMs = now;
        } else {
          wingManager.doAbsoluteAngleMotion(
              new AbsoluteAngleWingMotion(AbsoluteAngleWingMotion.PART_LEFT, 5, targetLeftAngle));
        }
      } else if (needResync) {
        wingManager.doNoAngleMotion(
            new NoAngleWingMotion(NoAngleWingMotion.PART_LEFT, 5, NoAngleWingMotion.ACTION_RESET));
        lastMirrorResetMs = now;
      }
      if (rightChanged) {
        lastRightMirrorAngle = targetRightAngle;
        if (targetRightAngle <= 0) {
          wingManager.doNoAngleMotion(
              new NoAngleWingMotion(NoAngleWingMotion.PART_RIGHT, 5, NoAngleWingMotion.ACTION_RESET));
          lastMirrorResetMs = now;
        } else {
          wingManager.doAbsoluteAngleMotion(
              new AbsoluteAngleWingMotion(AbsoluteAngleWingMotion.PART_RIGHT, 5, targetRightAngle));
        }
      } else if (needResync) {
        wingManager.doNoAngleMotion(
            new NoAngleWingMotion(NoAngleWingMotion.PART_RIGHT, 5, NoAngleWingMotion.ACTION_RESET));
        lastMirrorResetMs = now;
      }
    }
  }

  public void resetWings() {
    currentLeftWingUp = false;
    currentRightWingUp = false;
    lastLeftMirrorAngle = 0;
    lastRightMirrorAngle = 0;
    if (wingManager != null) {
      wingManager.doNoAngleMotion(
          new NoAngleWingMotion(NoAngleWingMotion.PART_BOTH, 5, NoAngleWingMotion.ACTION_RESET));
    }
  }
}
