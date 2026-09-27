package com.felipe.elftemplate.logic;

import com.felipe.elftemplate.tracking.KinectTrackingEngine.GestureType;
import java.util.Random;

/**
 * Motor de lógica para o jogo Pose Match / Just Dance estilo Kinect. Desafia o jogador a espelhar e
 * reproduzir poses corporais no tempo limite.
 */
public class PoseMatchGameEngine {

  private static final GestureType[] AVAILABLE_POSES = {
    GestureType.T_POSE,
    GestureType.HANDS_UP,
    GestureType.JUMP,
    GestureType.DUCK,
    GestureType.SWIPE_LEFT,
    GestureType.SWIPE_RIGHT
  };

  private final Random random = new Random();
  private GestureType currentTargetPose = GestureType.T_POSE;
  private float timeLimit = 6.0f;
  private float timeRemaining = 6.0f;
  private int score = 0;
  private int streak = 0;
  private int round = 1;
  private boolean isRoundComplete = false;
  private boolean isGameOver = false;

  public PoseMatchGameEngine() {
    pickNextTargetPose();
  }

  public void setTargetPose(GestureType pose, float timeLimit) {
    this.currentTargetPose = pose;
    this.timeLimit = timeLimit;
    this.timeRemaining = timeLimit;
    this.isRoundComplete = false;
  }

  public void pickNextTargetPose() {
    int idx = random.nextInt(AVAILABLE_POSES.length);
    currentTargetPose = AVAILABLE_POSES[idx];
    // Reduz o tempo gradualmente com base no round
    timeLimit = Math.max(3.0f, 6.0f - (round * 0.25f));
    timeRemaining = timeLimit;
    isRoundComplete = false;
  }

  public void update(float dt) {
    if (isGameOver || isRoundComplete) return;

    timeRemaining -= dt;
    if (timeRemaining <= 0) {
      timeRemaining = 0;
      streak = 0; // Perde o streak ao estourar o tempo
    }
  }

  public boolean evaluatePlayerPose(GestureType playerPose, float holdDuration) {
    if (isGameOver || isRoundComplete || isRoundTimedOut()) return false;

    if (playerPose == currentTargetPose && playerPose != GestureType.IDLE) {
      isRoundComplete = true;
      streak++;
      round++;
      // Pontuação com bônus de velocidade e streak
      int speedBonus = (int) (timeRemaining * 10);
      score += 100 + (streak * 20) + speedBonus;
      return true;
    }

    return false;
  }

  public boolean isRoundTimedOut() {
    return timeRemaining <= 0;
  }

  public GestureType getCurrentTargetPose() {
    return currentTargetPose;
  }

  public float getTimeRemaining() {
    return timeRemaining;
  }

  public float getTimeLimit() {
    return timeLimit;
  }

  public int getScore() {
    return score;
  }

  public int getStreak() {
    return streak;
  }

  public int getRound() {
    return round;
  }

  public boolean isRoundComplete() {
    return isRoundComplete;
  }

  public boolean isGameOver() {
    return isGameOver;
  }
}
