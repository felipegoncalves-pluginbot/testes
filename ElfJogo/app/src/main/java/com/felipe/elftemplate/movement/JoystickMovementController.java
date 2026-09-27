package com.felipe.elftemplate.movement;

import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;

/**
 * Controlador de movimentação do Joystick (WASD). Implementa filtragem de transição de estado e
 * watchdog de segurança.
 */
public class JoystickMovementController {
  private final RobotMovement robotMovement;
  private final SensorFusionEngine sensorEngine;
  private final Logger logger;

  private String lastCommand = "stop";
  private long lastCommandTimeMs = 0;
  private int speed = 6;

  public JoystickMovementController(
      RobotMovement robotMovement, SensorFusionEngine sensorEngine, Logger logger) {
    this.robotMovement = robotMovement;
    this.sensorEngine = sensorEngine;
    this.logger = logger;
  }

  /**
   * Processa comandos manuais de joystick.
   *
   * @param command Comando de movimentação ("forward", "backward", "left", "right", "stop").
   */
  public synchronized void handleCommand(String command) {
    if (command == null) return;
    command = command.trim();

    // Atualiza timestamp para o keep-alive do watchdog
    lastCommandTimeMs = System.currentTimeMillis();

    // Evita re-enviar comandos idênticos para não redefinir continuamente as rampas dos motores no
    // MCU
    if (command.equals(lastCommand)) {
      return;
    }

    logger.d("JoystickCtrl", "Comando alterado: " + lastCommand + " -> " + command);
    lastCommand = command;
    sensorEngine.setWheelCommand(command);

    byte action = NoAngleWheelMotion.ACTION_STOP;
    if ("forward".equals(command)) {
      action = NoAngleWheelMotion.ACTION_FORWARD;
    } else if ("backward".equals(command)) {
      action = NoAngleWheelMotion.ACTION_BACK;
    } else if ("left".equals(command)) {
      // v35: Usa ACTION_TURN_LEFT para curvas/giros mais robustos e sem travamentos no Sanbot Elf
      action = NoAngleWheelMotion.ACTION_LEFT;
    } else if ("right".equals(command)) {
      // v35: Usa ACTION_TURN_RIGHT para curvas/giros mais robustos e sem travamentos no Sanbot Elf
      action = NoAngleWheelMotion.ACTION_RIGHT;
    } else if ("stop".equals(command)) {
      action = NoAngleWheelMotion.ACTION_STOP;
    }

    // duration = 0: O robô continua se movendo continuamente até receber ACTION_STOP
    robotMovement.doNoAngleMotion(new NoAngleWheelMotion(action, speed, 0));
  }

  /** Watchdog de segurança. Para o robô se as comunicações com o cliente de controle falharem. */
  public synchronized void checkWatchdog() {
    if ("stop".equals(lastCommand)) {
      return;
    }

    long elapsed = System.currentTimeMillis() - lastCommandTimeMs;
    if (elapsed > 500) {
      logger.w(
          "JoystickCtrl",
          "Watchdog ativado: nenhum sinal de comando recebido por "
              + elapsed
              + "ms. Parando robô.");
      lastCommand = "stop";
      sensorEngine.setWheelCommand("stop");
      robotMovement.doNoAngleMotion(
          new NoAngleWheelMotion(NoAngleWheelMotion.ACTION_STOP, speed, 0));
    }
  }

  public synchronized String getLastCommand() {
    return lastCommand;
  }

  public synchronized void setSpeed(int speed) {
    this.speed = speed;
  }
}
