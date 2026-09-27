package com.felipe.elftemplate.logic;

/**
 * Engine responsável pela lógica do jogo de tênis. Gerencia a posição da bola e as colisões com os
 * jogadores.
 */
public class TennisGameEngine {
  private float ballX = 0.5f;
  private float ballY = 0.5f;
  private float speedX = 0.02f;
  private float speedY = 0.03f;
  private float baseSpeed = 0.03f;

  private int playerScore = 0;
  private int robotScore = 0;
  private int hits = 0;

  private boolean isWaitingToStart = true;

  public boolean update() {
    if (isWaitingToStart) return false;

    ballX += speedX;
    ballY += speedY;

    // Colisão com as paredes laterais
    if (ballX <= 0.05f || ballX >= 0.95f) {
      speedX = -speedX;
    }

    return checkScores();
  }

  private boolean checkScores() {
    if (ballY >= 0.95f) {
      robotScore++;
      resetBall();
      return true;
    }
    if (ballY <= 0.05f) {
      playerScore++;
      resetBall();
      return true;
    }
    return false;
  }

  public void resetBall() {
    ballX = 0.5f;
    ballY = 0.2f; // Começa mais perto do robô
    hits = 0;
    speedY = baseSpeed;
    speedX = (float) (Math.random() * 0.04f - 0.02f);
    isWaitingToStart = true;
  }

  public void onPlayerHit(float playerX) {
    // Aumentado para 0.35f (mais fácil de rebater)
    // Detecta batida na zona inferior (campo do jogador)
    if (Math.abs(playerX - ballX) < 0.35f && ballY > 0.7f) {
      speedY = -Math.abs(speedY + 0.002f); // Aumenta velocidade a cada rebatida
      speedX = (ballX - playerX) * 0.15f; // Dá direção à bola
      hits++;
    }
  }

  public void onRobotHit() {
    if (ballY < 0.3f) {
      speedY = Math.abs(speedY);
      // Robô também devolve com um pouco de aleatoriedade
      speedX += (Math.random() * 0.02f - 0.01f);
    }
  }

  public void start() {
    isWaitingToStart = false;
  }

  public float getBallX() {
    return ballX;
  }

  public float getBallY() {
    return ballY;
  }

  public int getPlayerScore() {
    return playerScore;
  }

  public int getRobotScore() {
    return robotScore;
  }

  public boolean isWaitingToStart() {
    return isWaitingToStart;
  }
}
