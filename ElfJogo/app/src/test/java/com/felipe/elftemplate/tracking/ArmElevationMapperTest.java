package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Mapeamento elevação → ângulo absoluto Sanbot (0° abaixo, 90° horizontal). */
public class ArmElevationMapperTest {

  @Test
  public void mapsZeroAndOneToSdkReferenceAngles() {
    for (float t = 0f; t <= 1f; t += 0.25f) {
      int angle = ArmElevationMapper.elevationToWingAngle(t);
      assertTrue(angle >= ArmElevationMapper.WING_ANGLE_MIN);
      assertTrue(angle <= ArmElevationMapper.WING_ANGLE_MAX);
    }
    assertEquals(0, ArmElevationMapper.elevationToWingAngle(0f));
    assertEquals(90, ArmElevationMapper.elevationToWingAngle(1f));
    assertEquals(45, ArmElevationMapper.elevationToWingAngle(0.5f));
  }
}
