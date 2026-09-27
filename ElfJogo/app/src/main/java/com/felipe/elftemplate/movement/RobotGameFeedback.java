package com.felipe.elftemplate.movement;

import com.felipe.elftemplate.logic.HeadGazeServo;
import com.sanbot.opensdk.function.beans.LED;
import com.sanbot.opensdk.function.beans.wheelmotion.RelativeAngleWheelMotion;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.HeadMotionManager;
import com.sanbot.opensdk.function.unit.SpeechManager;
import com.sanbot.opensdk.function.unit.WheelMotionManager;
import com.sanbot.opensdk.function.unit.WingMotionManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Fachada leve de Feedback Físico, Visual, Sonoro e Teleoperação da Cabeça do Sanbot Elf. */
public class RobotGameFeedback {

  public static final int SANBOT_HEAD_CENTER_YAW = RobotHeadController.SANBOT_HEAD_CENTER_YAW;
  public static final int SANBOT_HEAD_LEVEL_PITCH = RobotHeadController.SANBOT_HEAD_LEVEL_PITCH;
  public static final int MIN_SAFE_HARDWARE_YAW = RobotHeadController.MIN_SAFE_HARDWARE_YAW;
  public static final int MAX_SAFE_HARDWARE_YAW = RobotHeadController.MAX_SAFE_HARDWARE_YAW;
  public static final int MIN_SAFE_HARDWARE_PITCH = RobotHeadController.MIN_SAFE_HARDWARE_PITCH;
  public static final int MAX_SAFE_HARDWARE_PITCH = RobotHeadController.MAX_SAFE_HARDWARE_PITCH;

  private final SpeechManager speechManager;
  private final HardWareManager hardWareManager;
  private final WheelMotionManager wheelManager;
  private final RobotHeadController headController;
  private final RobotWingController wingController;
  private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
  private final HeadGazeServo gazeServo = new HeadGazeServo();
  private volatile boolean stopped;

  private long lastSpeechTime = 0;
  private long lastWheelRotationTime = 0;

  public RobotGameFeedback(
      SpeechManager speech,
      WingMotionManager wing,
      HeadMotionManager head,
      HardWareManager hardware) {
    this(speech, wing, head, null, hardware);
  }

  public RobotGameFeedback(
      SpeechManager speech,
      WingMotionManager wing,
      HeadMotionManager head,
      WheelMotionManager wheel,
      HardWareManager hardware) {
    this.speechManager = speech;
    this.wheelManager = wheel;
    this.hardWareManager = hardware;
    this.headController = new RobotHeadController(head);
    this.wingController = new RobotWingController(wing);
  }

  public static int calculateHardwareYaw(int yawOffset) {
    return RobotHeadController.calculateHardwareYaw(yawOffset);
  }

  public static int calculateHardwarePitch(int pitchOffset) {
    return RobotHeadController.calculateHardwarePitch(pitchOffset);
  }

  public void setMirrorHead2D(int targetYawOffset, int targetPitchOffset) {
    enqueue(() -> headController.updateMirrorHeadAngle(targetYawOffset, targetPitchOffset));
  }

  public void setMirrorWings(boolean leftWingUp, boolean rightWingUp) {
    enqueue(() -> wingController.updateWings(leftWingUp, rightWingUp));
  }

  public void setMirrorWingAngles(int leftAngle, int rightAngle) {
    enqueue(() -> wingController.updateMirrorAngles(leftAngle, rightAngle));
  }

  public void resetWings() {
    enqueue(() -> wingController.resetWings());
  }

  public void resetCenter() {
    enqueue(() -> headController.resetCenter());
  }

  /**
   * Vira a cabeça para manter o jogador no quadro do Astra, que fica na própria cabeça.
   *
   * <p>Antes o X virava yaw absoluto a cada frame ({@code (0,5 − x)·60}): girar a cabeça movia o
   * jogador na imagem e o comando voltava, e a cabeça ficava indo e vindo o jogo inteiro. Agora só
   * sai comando quando o jogador deixa a zona central, em passos que esperam a cabeça assentar.
   * Chamado da UI e da ponte JavaScript dos jogos web, por isso é sincronizado.
   */
  public synchronized void trackTargetX(float centroidX) {
    int before = gazeServo.getYawOffset();
    int yawOffset = gazeServo.update(centroidX, System.currentTimeMillis());
    if (yawOffset != before) {
      setMirrorHead2D(yawOffset, 0);
    }
  }

  public void performHeadNodYes() {
    enqueue(
        () -> {
          headController.updateMirrorHeadAngle(0, -8);
          try {
            Thread.sleep(200);
          } catch (InterruptedException ignored) {
          }
          headController.updateMirrorHeadAngle(0, 8);
          try {
            Thread.sleep(200);
          } catch (InterruptedException ignored) {
          }
          headController.resetCenter();
        });
  }

  public void performHeadShakeNo() {
    enqueue(
        () -> {
          headController.updateMirrorHeadAngle(-25, 0);
          try {
            Thread.sleep(200);
          } catch (InterruptedException ignored) {
          }
          headController.updateMirrorHeadAngle(25, 0);
          try {
            Thread.sleep(200);
          } catch (InterruptedException ignored) {
          }
          headController.resetCenter();
        });
  }

  public void performPoseMirror(boolean leftArmUp, boolean rightArmUp) {
    setMirrorWings(leftArmUp, rightArmUp);
  }

  public void cheerPoint(boolean isPlayerWinner, String phrase) {
    setLedColor(LED.PART_ALL, LED.MODE_GREEN);
    speak(phrase);
  }

  public void reactBombOrMiss(String phrase) {
    setLedColor(LED.PART_ALL, LED.MODE_RED);
    speak(phrase);
  }

  public void speak(String text) {
    long now = System.currentTimeMillis();
    if (now - lastSpeechTime < 1500) return;
    lastSpeechTime = now;
    enqueue(
        () -> {
          if (speechManager != null) speechManager.startSpeak(text);
        });
  }

  public void setLedColor(byte part, byte color) {
    enqueue(
        () -> {
          if (hardWareManager != null) {
            hardWareManager.setLED(new LED(part, color));
          }
        });
  }

  public void rotateBaseRelative(int angleDeg) {
    long now = System.currentTimeMillis();
    if (now - lastWheelRotationTime < 1200) return;
    lastWheelRotationTime = now;
    enqueue(
        () -> {
          if (wheelManager != null) {
            byte action =
                (angleDeg < 0)
                    ? RelativeAngleWheelMotion.TURN_LEFT
                    : RelativeAngleWheelMotion.TURN_RIGHT;
            wheelManager.doRelativeAngleMotion(
                new RelativeAngleWheelMotion(action, 5, Math.abs(angleDeg)));
          }
        });
  }

  public void stopAll() {
    release();
  }

  public boolean isStopped() {
    return stopped;
  }

  /**
   * Para o pool. Tracking ainda pode postar na UI após {@code onStop} — enqueue ignora, não lança
   * {@code RejectedExecutionException} (FATAL TennisActivity).
   */
  public void release() {
    if (stopped) {
      return;
    }
    stopped = true;
    try {
      headController.resetCenter();
      wingController.resetWings();
    } catch (Throwable ignored) {
    }
    commandExecutor.shutdown();
  }

  private void enqueue(Runnable task) {
    if (task == null || stopped || commandExecutor.isShutdown()) {
      return;
    }
    try {
      commandExecutor.execute(task);
    } catch (RejectedExecutionException ignored) {
    }
  }
}
