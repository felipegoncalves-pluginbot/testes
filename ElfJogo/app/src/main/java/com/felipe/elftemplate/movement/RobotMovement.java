package com.felipe.elftemplate.movement;

import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;

/** Interface para abstração de movimentos do robô. */
public interface RobotMovement {
  void doNoAngleMotion(NoAngleWheelMotion motion);
}
