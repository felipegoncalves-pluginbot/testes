package com.felipe.elftemplate.movement;

import com.sanbot.opensdk.function.beans.wheelmotion.NoAngleWheelMotion;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controlador de Navegação Autônoma (Piloto Automático). Malha Fechada (Closed-loop): Calcula a
 * rota continuamente baseado no Feedback do SLAM.
 */
public class NavigationController {

  private static final String TAG = "NavController";

  private RobotMovement robotMovement;
  private SensorFusionEngine sensorEngine;
  private Logger logger;
  private ScheduledExecutorService scheduler;

  private boolean isNavigating = false;
  private double targetX = 0;
  private double targetY = 0;
  private int loopCounter = 0;

  public NavigationController(
      RobotMovement robotMovement, SensorFusionEngine sensorEngine, Logger logger) {
    this.robotMovement = robotMovement;
    this.sensorEngine = sensorEngine;
    this.logger = logger;
  }

  private boolean isSpinning = false;
  private static final int ANGLE_THRESHOLD_ENTER = 35; // v24: Histerese
  private static final int ANGLE_THRESHOLD_EXIT = 8;

  // v32: Constantes Pure Pursuit
  private static final double LOOK_AHEAD_DIST = 0.5;
  private static final double ARRIVAL_THRESHOLD = 0.15; // 15cm precision
  private static final double PRECISION_MODE_DIST = 1.0; // 1 meter

  private final Runnable navigationLoop =
      new Runnable() {
        @Override
        public void run() {
          if (!isNavigating) return;

          try {
            double currentX = sensorEngine.getX();
            double currentY = sensorEngine.getY();
            double currentYaw = sensorEngine.getYawRad();

            double dx = targetX - currentX;
            double dy = targetY - currentY;
            double distanceMeters = Math.hypot(dx, dy);

            if (distanceMeters < ARRIVAL_THRESHOLD) {
              logger.i(TAG, "Chegou ao destino!");
              stopNavigation();
              return;
            }

            // v32: Obstacle Avoidance check before moving
            if (sensorEngine.isPathBlocked(0.6)) {
              logger.w(TAG, "NAV -> CAMINHO BLOQUEADO! Parando robô.");
              robotMovement.doNoAngleMotion(
                  new NoAngleWheelMotion(NoAngleWheelMotion.ACTION_STOP, 5, 0));
              sensorEngine.setWheelCommand("stop");
              return;
            }

            // v32: Pure Pursuit point calculation
            double targetYaw;
            if (distanceMeters > LOOK_AHEAD_DIST) {
              // Mira num ponto intermediário à frente
              double ratio = LOOK_AHEAD_DIST / distanceMeters;
              double lookX = currentX + dx * ratio;
              double lookY = currentY + dy * ratio;
              targetYaw = Math.atan2(lookY - currentY, lookX - currentX);
            } else {
              targetYaw = Math.atan2(dy, dx);
            }

            double relativeAngleRad = targetYaw - currentYaw;
            relativeAngleRad = Math.atan2(Math.sin(relativeAngleRad), Math.cos(relativeAngleRad));
            int angleDegrees = (int) Math.toDegrees(Math.abs(relativeAngleRad));

            byte action;
            int speed;

            // Lógica de Histerese para evitar "Spin Loop" (v24)
            if (!isSpinning) {
              if (angleDegrees > ANGLE_THRESHOLD_ENTER) {
                isSpinning = true;
              }
            } else {
              if (angleDegrees < ANGLE_THRESHOLD_EXIT) {
                isSpinning = false;
              }
            }

            if (isSpinning) {
              // Erro grande: Gira no próprio eixo
              action =
                  relativeAngleRad > 0
                      ? NoAngleWheelMotion.ACTION_LEFT
                      : NoAngleWheelMotion.ACTION_RIGHT;
              speed = angleDegrees > 60 ? 6 : 4;
            } else if (angleDegrees > 15) {
              // v32: Erro médio: Usa ACTION_TURN para curvas fluidas
              action =
                  relativeAngleRad > 0
                      ? NoAngleWheelMotion.ACTION_TURN_LEFT
                      : NoAngleWheelMotion.ACTION_TURN_RIGHT;
              speed = 4;
            } else {
              // Erro pequeno: Segue em frente
              action = NoAngleWheelMotion.ACTION_FORWARD;
              speed = distanceMeters > 0.6 ? 6 : 4;
            }

            String cmdStr = getCommandString(action);

            // Manda o motor rodar com duração de 500ms para loop de 100ms
            // v19: Log em alta frequência para diagnosticar waypoints
            logger.d(
                TAG,
                String.format(
                    "NAV -> Dist: %.2fm | ErrAng: %d deg | Pose: (%.2f, %.2f) | Target: (%.2f, %.2f) | CMD: %s",
                    distanceMeters,
                    (int) Math.toDegrees(relativeAngleRad),
                    currentX,
                    currentY,
                    targetX,
                    targetY,
                    cmdStr));

            if (distanceMeters > 0.05) {
              robotMovement.doNoAngleMotion(new NoAngleWheelMotion(action, speed, 5));
            } else {
              robotMovement.doNoAngleMotion(
                  new NoAngleWheelMotion(NoAngleWheelMotion.ACTION_STOP, 0, 0));
            }
            sensorEngine.setWheelCommand(cmdStr);

            loopCounter++;
          } catch (Exception e) {
            logger.e(TAG, "Erro no loop de navegação: " + e.getMessage());
          }
        }

        private String getCommandString(byte action) {
          switch (action) {
            case NoAngleWheelMotion.ACTION_FORWARD:
              return "forward";
            case NoAngleWheelMotion.ACTION_BACK:
              return "backward";
            case NoAngleWheelMotion.ACTION_LEFT:
              return "left";
            case NoAngleWheelMotion.ACTION_RIGHT:
              return "right";
            case NoAngleWheelMotion.ACTION_TURN_LEFT:
              return "turn_left";
            case NoAngleWheelMotion.ACTION_TURN_RIGHT:
              return "turn_right";
            default:
              return "stop";
          }
        }
      };

  public void goToTarget(double targetX, double targetY) {
    this.targetX = targetX;
    this.targetY = targetY;
    this.isNavigating = true;
    logger.d(TAG, "Iniciando navegação autônoma para: " + targetX + ", " + targetY);

    if (scheduler == null || scheduler.isShutdown()) {
      scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    scheduler.scheduleAtFixedRate(navigationLoop, 0, 100, TimeUnit.MILLISECONDS);
  }

  public void returnToHome() {
    goToTarget(0.0, 0.0);
  }

  public boolean isNavigating() {
    return isNavigating;
  }

  public void stopNavigation() {
    if (!isNavigating) return;

    isNavigating = false;
    if (scheduler != null) {
      scheduler.shutdownNow();
      scheduler = null;
    }

    // v13: Limpa o sinalizador de interrupção e adiciona um pequeno delay para o scheduler limpar a
    // thread
    Thread.interrupted();

    if (sensorEngine != null) {
      sensorEngine.setWheelCommand("stop");
    }
    if (robotMovement != null) {
      // v12: Garantir parada imediata com velocidade 5 e duração 0
      robotMovement.doNoAngleMotion(new NoAngleWheelMotion(NoAngleWheelMotion.ACTION_STOP, 5, 0));
    }
  }

  public double getTargetX() {
    return targetX;
  }

  public double getTargetY() {
    return targetY;
  }

  private boolean isCurrentlyRotating(String command) {
    return "left".equals(command) || "right".equals(command);
  }
}
