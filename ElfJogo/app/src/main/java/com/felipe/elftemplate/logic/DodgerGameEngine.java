package com.felipe.elftemplate.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Motor de lógica para o jogo Dodger / Body Runner estilo Kinect Adventures. O jogador desvia de
 * barreiras inclinando o corpo (X), pulando barreiras baixas ou agachando sob lasers.
 */
public class DodgerGameEngine {

  public enum ObstacleType {
    LOW_HURDLE, // Requer PULO
    HIGH_BARRIER, // Requer AGACHAMENTO (Duck)
    LEFT_BLOCK, // Requer INCLINAÇÃO PARA A DIREITA
    RIGHT_BLOCK // Requer INCLINAÇÃO PARA A ESQUERDA
  }

  public static class Obstacle {
    public ObstacleType type;
    public float x;
    public float y;
    public float width;
    public float height;
    public boolean isCleared = false;

    public Obstacle(ObstacleType type, float x, float y, float width, float height) {
      this.type = type;
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }
  }

  private static final float HIT_ZONE_MIN_Y = 0.70f;
  private static final float HIT_ZONE_MAX_Y = 0.90f;
  private static final float SCROLL_SPEED = 0.012f;

  private final List<Obstacle> activeObstacles = new ArrayList<>();
  private final Random random = new Random();

  private int score = 0;
  private float distance = 0;
  private int lives = 3;
  private boolean gameOver = false;
  private float spawnTimer = 0;

  public DodgerGameEngine() {}

  public void reset() {
    activeObstacles.clear();
    score = 0;
    distance = 0;
    lives = 3;
    gameOver = false;
    spawnTimer = 0;
  }

  public void update(float dt) {
    if (gameOver) return;

    distance += dt * 0.1f;
    score = (int) distance;

    spawnTimer += dt * 0.016f;
    if (spawnTimer >= 2.0f) { // Spawna obstáculo a cada 2 segundos
      spawnRandomObstacle();
      spawnTimer = 0;
    }

    Iterator<Obstacle> it = activeObstacles.iterator();
    while (it.hasNext()) {
      Obstacle obs = it.next();
      obs.y += SCROLL_SPEED * dt;

      if (obs.y > 1.2f) {
        it.remove();
      }
    }
  }

  public void spawnObstacle(ObstacleType type, float x, float y) {
    float w = (type == ObstacleType.LOW_HURDLE || type == ObstacleType.HIGH_BARRIER) ? 0.7f : 0.4f;
    float h = 0.08f;
    activeObstacles.add(new Obstacle(type, x, y, w, h));
  }

  private void spawnRandomObstacle() {
    int r = random.nextInt(4);
    ObstacleType type = ObstacleType.values()[r];
    float x = 0.5f;
    if (type == ObstacleType.LEFT_BLOCK) x = 0.25f;
    else if (type == ObstacleType.RIGHT_BLOCK) x = 0.75f;

    spawnObstacle(type, x, -0.1f);
  }

  /** Valida colisão entre o corpo do jogador e os obstáculos na zona de impacto. */
  public boolean checkCollision(float playerX, boolean isJumping, boolean isDucking) {
    if (gameOver) return false;

    for (Obstacle obs : activeObstacles) {
      if (obs.isCleared) continue;

      // Verifica se está na zona vertical de impacto (próximo do chão)
      if (obs.y >= HIT_ZONE_MIN_Y && obs.y <= HIT_ZONE_MAX_Y) {
        boolean collision = false;

        switch (obs.type) {
          case LOW_HURDLE:
            // Obstáculo no chão: bate se NÃO estiver pulando
            if (!isJumping) {
              collision = true;
            }
            break;

          case HIGH_BARRIER:
            // Obstáculo no alto: bate se NÃO estiver agachado
            if (!isDucking) {
              collision = true;
            }
            break;

          case LEFT_BLOCK:
            // Bloco no lado esquerdo (x < 0.5): bate se o jogador estiver no lado esquerdo
            if (playerX < 0.45f && !isJumping) {
              collision = true;
            }
            break;

          case RIGHT_BLOCK:
            // Bloco no lado direito (x > 0.5): bate se o jogador estiver no lado direito
            if (playerX > 0.55f && !isJumping) {
              collision = true;
            }
            break;
        }

        if (collision) {
          obs.isCleared = true;
          lives--;
          if (lives <= 0) {
            gameOver = true;
          }
          return true;
        } else if (obs.y > HIT_ZONE_MAX_Y - 0.05f) {
          obs.isCleared = true; // Desviou com sucesso!
          score += 50;
        }
      }
    }

    return false;
  }

  public List<Obstacle> getActiveObstacles() {
    return activeObstacles;
  }

  public int getScore() {
    return score;
  }

  public float getDistance() {
    return distance;
  }

  public int getLives() {
    return lives;
  }

  public boolean isGameOver() {
    return gameOver;
  }
}
