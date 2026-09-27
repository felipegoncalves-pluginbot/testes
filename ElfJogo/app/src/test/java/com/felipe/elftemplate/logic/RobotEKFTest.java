package com.felipe.elftemplate.logic;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class RobotEKFTest {

  private RobotEKF ekf;

  @Before
  public void setUp() {
    ekf = new RobotEKF();
  }

  @Test
  public void testInitialization() {
    assertArrayEquals(new float[] {0f, 0f, 0f}, ekf.getState(), 0.001f);
  }

  @Test
  public void testVisualOdometryUpdateOnly() {
    // Sem bússola, a VO deve ditar o estado 100% se a covariância for quase 0
    ekf.updateVisualOdometry(1.0f, 2.0f, (float) Math.PI / 4, 0.0001);
    float[] state = ekf.getState();
    assertEquals(1.0f, state[0], 0.05f);
    assertEquals(2.0f, state[1], 0.05f);
    assertEquals((float) Math.PI / 4, state[2], 0.05f);
  }

  @Test
  public void testCompassFusion_RejectsOutliers() {
    // Cenário 2: Distorção Magnética (Rejeição de outlier)
    // VO diz 0 graus, Bússola diz 0 graus
    ekf.updateVisualOdometry(0f, 0f, 0f, 0.1);
    ekf.updateCompass(0f, 0.1);

    // Bússola repentinamente salta para 90 graus (ruído / distorção)
    ekf.updateCompass((float) Math.PI / 2, 999.0); // Covariância alta significa ruído

    float[] state = ekf.getState();
    // O EKF deve manter o Yaw próximo de 0 devido à alta variância da bússola nesse instante
    assertEquals(0f, state[2], 0.1f);
  }

  @Test
  public void testCompassFusion_CorrectsDrift() {
    // Cenário 1: Deriva da VO
    // A VO diz que girou levemente (deriva), mas a bússola com alta confiança diz que está reto.
    ekf.updateVisualOdometry(1f, 0f, 0.1f, 0.5); // VO tem covariância 0.5
    ekf.updateCompass(0f, 0.01); // Bússola tem covariância 0.01 (muito confiável)

    float[] state = ekf.getState();
    // O Yaw deve ser tracionado fortemente para 0 (Bússola)
    assertEquals(0f, state[2], 0.05f);
  }
}
