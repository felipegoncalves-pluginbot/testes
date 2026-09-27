package com.felipe.elftemplate.movement;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

/** Testes unitários para cálculo de odometria de rodas e atualização de coordenadas do robô. */
public class WheelOdometryProcessorTest {

  private WheelOdometryProcessor processor;

  @Before
  public void setUp() {
    processor = new WheelOdometryProcessor();
  }

  @Test
  public void testForwardDisplacementAccumulation() {
    processor.reset(0f, 0f, 0f);

    // Anda 1.0 metro para frente (heading = 0 rad)
    processor.updateWithWheelDisplacement(1.0f, 0.0f);

    assertEquals(1.0f, processor.getEstimatedX(), 0.01f);
    assertEquals(0.0f, processor.getEstimatedY(), 0.01f);
  }

  @Test
  public void testHeadingIntegration() {
    processor.reset(0f, 0f, 0f);

    // Gira 90 graus (PI/2 radianos) e anda 1.0 metro
    processor.updateWithWheelDisplacement(1.0f, (float) (Math.PI / 2.0));

    assertEquals(0.0f, processor.getEstimatedX(), 0.05f);
    assertEquals(1.0f, processor.getEstimatedY(), 0.05f);
  }
}
