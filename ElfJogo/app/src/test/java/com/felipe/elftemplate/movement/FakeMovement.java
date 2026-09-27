package com.felipe.elftemplate.movement;

import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;
import java.util.ArrayList;
import java.util.List;

/** Fake para testes de movimento. */
public class FakeMovement implements RobotMovement {
  public List<NoAngleWheelMotion> commands = new ArrayList<>();

  @Override
  public void doNoAngleMotion(NoAngleWheelMotion motion) {
    commands.add(motion);
  }

  public NoAngleWheelMotion getLastCommand() {
    return commands.isEmpty() ? null : commands.get(commands.size() - 1);
  }
}
