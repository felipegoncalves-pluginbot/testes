package com.felipe.elftemplate.movement;

import static org.junit.Assert.*;

import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;
import org.junit.Before;
import org.junit.Test;

/** Testes unitários para o JoystickMovementController (TDD). */
public class JoystickMovementControllerTest {
  private JoystickMovementController controller;
  private FakeMovement movement;
  private SensorFusionEngine engine;
  private ConsoleLogger logger;

  @Before
  public void setUp() {
    logger = new ConsoleLogger();
    movement = new FakeMovement();
    engine = new SensorFusionEngine(logger);
    controller = new JoystickMovementController(movement, engine, logger);
  }

  @Test
  public void testPrintConstants() {
    for (java.lang.reflect.Constructor<?> constructor :
        NoAngleWheelMotion.class.getConstructors()) {
      System.out.println("CONSTRUCTOR: " + constructor.toGenericString());
      for (java.lang.reflect.Parameter param : constructor.getParameters()) {
        System.out.println("  PARAM: " + param.getType().getName() + " " + param.getName());
      }
    }
  }

  @Test
  public void testAvoidSpammingCommands() {
    // Envia 10 comandos idênticos seguidos
    for (int i = 0; i < 10; i++) {
      controller.handleCommand("forward");
    }
    // Garante que doNoAngleMotion foi chamado exatamente 1 vez
    assertEquals(1, movement.commands.size());
    assertEquals(NoAngleWheelMotion.ACTION_FORWARD, movement.getLastCommand().getAction());
  }

  @Test
  public void testImmediateCommandTransition() {
    controller.handleCommand("forward");
    controller.handleCommand("left");

    // Garante que ambos os comandos foram repassados imediatamente
    assertEquals(2, movement.commands.size());
    assertEquals(NoAngleWheelMotion.ACTION_FORWARD, movement.commands.get(0).getAction());
    assertEquals(NoAngleWheelMotion.ACTION_LEFT, movement.commands.get(1).getAction());
  }

  @Test
  public void testWatchdogTriggersStop() throws InterruptedException {
    controller.handleCommand("forward");
    assertEquals(1, movement.commands.size());

    // Watchdog não deve disparar se não passou o tempo limite (500ms)
    controller.checkWatchdog();
    assertEquals(1, movement.commands.size());

    // Simula passagem de tempo de 600ms
    Thread.sleep(600);
    controller.checkWatchdog();

    // Agora o watchdog deve ter disparado um comando ACTION_STOP
    assertEquals(2, movement.commands.size());
    assertEquals(NoAngleWheelMotion.ACTION_STOP, movement.getLastCommand().getAction());
  }
}
