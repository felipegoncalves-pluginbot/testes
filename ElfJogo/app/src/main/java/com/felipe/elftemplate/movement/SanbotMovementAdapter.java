package com.felipe.elftemplate.movement;

import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;
import com.sanbot.opensdk.function.unit.WheelMotionManager;

/** Adaptador para o WheelMotionManager do Sanbot SDK. */
public class SanbotMovementAdapter implements RobotMovement {
  private WheelMotionManager wheelMotionManager;

  public SanbotMovementAdapter(WheelMotionManager wheelMotionManager) {
    this.wheelMotionManager = wheelMotionManager;
  }

  @Override
  public void doNoAngleMotion(NoAngleWheelMotion motion) {
    if (wheelMotionManager != null) {
      // v15: Logs extremamente detalhados para debug de bloqueio
      android.util.Log.d(
          "SanbotAdapter",
          String.format("CMD -> Action: %d, Speed: %d", motion.getAction(), motion.getSpeed()));
      wheelMotionManager.doNoAngleMotion(motion);
    } else {
      android.util.Log.e("SanbotAdapter", "FATAL: wheelMotionManager is NULL");
    }
  }
}
