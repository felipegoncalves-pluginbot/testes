package com.felipe.elftemplate.movement;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class NavigationControllerTest {

  private NavigationController controller;
  private FakeMovement movement;
  private SensorFusionEngine engine;
  private ConsoleLogger logger;

  @Before
  public void setUp() {
    logger = new ConsoleLogger();
    movement = new FakeMovement();
    engine = new SensorFusionEngine(logger);
    controller = new NavigationController(movement, engine, logger);
  }

  @Test
  public void testTargetAngleCalculation() {
    controller.goToTarget(1.0, 1.0);
    double dist = Math.hypot(1.0, 1.0);
    assertTrue("Distância ao alvo deve ser positiva", dist > 0.5);
    assertEquals("X inicial deve ser 0", 0.0, engine.getX(), 0.001);
  }

  @Test
  public void testArrivedCondition() {
    engine.reset(); // At (0,0)
    controller.goToTarget(0.05, 0.05); // Within 10cm tolerance

    double dist = Math.hypot(0.05 - 0, 0.05 - 0);
    assertTrue(dist < 0.10);
    assertEquals("Posição Y inicial deve ser 0", 0.0, engine.getY(), 0.001);
  }

  @Test
  public void testPrecisionModeToggle() {
    engine.reset(); // (0,0)
    controller.goToTarget(5.0, 5.0); // Far away
    try {
      Thread.sleep(150);
    } catch (InterruptedException e) {
    }

    controller.stopNavigation();
    engine.reset();
    controller.goToTarget(0.2, 0.2); // Very close (< 1m)
    try {
      Thread.sleep(150);
    } catch (InterruptedException e) {
    }
    controller.stopNavigation();
    assertEquals("Comando parado após navegação", "stop", engine.getWheelCommand());
  }

  @Test
  public void testObstacleAvoidance() {
    engine.reset();
    // Simular um bloqueio frontal usando os sensores infravermelhos (ex: sensor 6) a 40cm
    engine.updateInfrared(6, 40);

    assertTrue(engine.isPathBlocked(0.6));

    controller.goToTarget(1.0, 0.0);
    try {
      Thread.sleep(150);
    } catch (InterruptedException e) {
    }

    // Deve estar "stop" por causa do bloqueio
    assertEquals("stop", engine.getWheelCommand());
  }

  @Test
  public void testTurnCommands() {
    engine.reset();
    // Alvo a 20 graus (Erro médio entre 15 e 35)
    // Math.atan2(y, x) = 20 deg -> y = x * tan(20)
    double y = Math.tan(Math.toRadians(20));
    controller.goToTarget(1.0, y);

    try {
      Thread.sleep(150);
    } catch (InterruptedException e) {
    }
    // v32: Deve usar turn_left
    assertEquals("turn_left", engine.getWheelCommand());
  }
}
