package com.felipe.elftemplate.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Motor de física e lógica para o jogo Fruit Slicer estilo Kinect/Fruit Ninja. */
public class FruitSlicerGameEngine {

  public enum ItemType {
    WATERMELON,
    APPLE,
    BANANA,
    BOMB
  }

  public static class GameItem {
    public ItemType type;
    public float x; // 0.0 a 1.0 (coordenadas de tela normalizadas)
    public float y;
    public float vx; // Velocidade horizontal
    public float vy; // Velocidade vertical
    public float radius; // Raio normalizado
    public float rotation;
    public float rotationSpeed;
    public boolean isSliced = false;
    public float sliceAngle = 0;
    public boolean isBomb = false;
    public float half1X, half1Y, half1Vx, half1Vy;
    public float half2X, half2Y, half2Vx, half2Vy;

    public GameItem(
        ItemType type, float x, float y, float vx, float vy, float radius, boolean isBomb) {
      this.type = type;
      this.x = x;
      this.y = y;
      this.vx = vx;
      this.vy = vy;
      this.radius = radius;
      this.isBomb = isBomb;
      this.rotation = 0;
      this.rotationSpeed = (float) ((Math.random() - 0.5) * 8.0);
    }
  }

  private static final float GRAVITY = 0.0018f;
  private static final long COMBO_TIMEOUT_MS = 600;

  private final List<GameItem> activeItems = new ArrayList<>();
  private final Random random = new Random();

  private int score = 0;
  private int combo = 0;
  private int lives = 3;
  private boolean gameOver = false;
  private long lastSliceTimestamp = 0;
  private float roundTimeRemaining = 60.0f; // 60 segundos por rodada

  public FruitSlicerGameEngine() {}

  public void reset() {
    activeItems.clear();
    score = 0;
    combo = 0;
    lives = 3;
    gameOver = false;
    roundTimeRemaining = 60.0f;
  }

  public void update(float dt) {
    if (gameOver) return;

    roundTimeRemaining -= (dt * 0.016f);
    if (roundTimeRemaining <= 0) {
      gameOver = true;
      return;
    }

    // Atualização de física das entidades
    Iterator<GameItem> it = activeItems.iterator();
    while (it.hasNext()) {
      GameItem item = it.next();

      if (!item.isSliced) {
        item.x += item.vx * dt;
        item.y += item.vy * dt;
        item.vy += GRAVITY * dt;
        item.rotation += item.rotationSpeed * dt;

        // Fruta caiu sem ser cortada
        if (item.y > 1.2f && item.vy > 0) {
          if (!item.isBomb) {
            combo = 0; // Perde o combo ao deixar fruta cair
          }
          it.remove();
        }
      } else {
        // Atualiza metades cortadas
        item.half1X += item.half1Vx * dt;
        item.half1Y += item.half1Vy * dt;
        item.half1Vy += GRAVITY * dt;

        item.half2X += item.half2Vx * dt;
        item.half2Y += item.half2Vy * dt;
        item.half2Vy += GRAVITY * dt;

        if (item.half1Y > 1.3f && item.half2Y > 1.3f) {
          it.remove();
        }
      }
    }

    // Verifica expiração de combo
    if (combo > 0 && System.currentTimeMillis() - lastSliceTimestamp > COMBO_TIMEOUT_MS) {
      combo = 0;
    }
  }

  /** Spawna onda aleatória de frutas e bombas na base da tela. */
  public void spawnWave() {
    if (gameOver) return;
    int count = 1 + random.nextInt(3);
    for (int i = 0; i < count; i++) {
      float startX = 0.2f + random.nextFloat() * 0.6f;
      float startY = 1.05f;
      float vx = (0.5f - startX) * 0.015f + (random.nextFloat() - 0.5f) * 0.01f;
      float vy = -0.038f - random.nextFloat() * 0.015f;
      boolean isBomb = random.nextFloat() < 0.20f; // 20% de chance de bomba

      ItemType type;
      if (isBomb) {
        type = ItemType.BOMB;
      } else {
        int r = random.nextInt(3);
        type = (r == 0) ? ItemType.WATERMELON : (r == 1 ? ItemType.APPLE : ItemType.BANANA);
      }

      activeItems.add(new GameItem(type, startX, startY, vx, vy, 0.07f, isBomb));
    }
  }

  /** Utilizado para testes unitários determinísticos. */
  public void spawnTestFruit(float x, float y, float vx, float vy, boolean isBomb) {
    ItemType type = isBomb ? ItemType.BOMB : ItemType.WATERMELON;
    activeItems.add(new GameItem(type, x, y, vx, vy, 0.08f, isBomb));
  }

  /**
   * Processa a passagem de uma linha de corte (segmento da mão do jogador) contra as frutas ativas.
   */
  public int processSlice(float x1, float y1, float x2, float y2) {
    if (gameOver) return 0;
    int slicedCount = 0;

    for (GameItem item : activeItems) {
      if (item.isSliced) continue;

      if (isSegmentIntersectingCircle(x1, y1, x2, y2, item.x, item.y, item.radius)) {
        item.isSliced = true;
        slicedCount++;

        float angle = (float) Math.atan2(y2 - y1, x2 - x1);
        item.sliceAngle = angle;

        float perpX = (float) -Math.sin(angle) * 0.015f;
        float perpY = (float) Math.cos(angle) * 0.015f;

        item.half1X = item.x;
        item.half1Y = item.y;
        item.half1Vx = item.vx + perpX;
        item.half1Vy = item.vy + perpY - 0.005f;

        item.half2X = item.x;
        item.half2Y = item.y;
        item.half2Vx = item.vx - perpX;
        item.half2Vy = item.vy - perpY - 0.005f;

        if (item.isBomb) {
          combo = 0;
          score = Math.max(0, score - 50);
          lives--;
          if (lives <= 0) {
            gameOver = true;
          }
        } else {
          combo++;
          score += 10 * combo;
          lastSliceTimestamp = System.currentTimeMillis();
        }
      }
    }

    return slicedCount;
  }

  /** Verifica se o segmento de reta (corte) intercepta o círculo (fruta). */
  private boolean isSegmentIntersectingCircle(
      float x1, float y1, float x2, float y2, float cx, float cy, float radius) {
    float dx = x2 - x1;
    float dy = y2 - y1;
    float lengthSq = dx * dx + dy * dy;

    if (lengthSq == 0) {
      float distSq = (cx - x1) * (cx - x1) + (cy - y1) * (cy - y1);
      return distSq <= radius * radius;
    }

    float t = Math.max(0, Math.min(1, ((cx - x1) * dx + (cy - y1) * dy) / lengthSq));
    float projX = x1 + t * dx;
    float projY = y1 + t * dy;

    float distSq = (cx - projX) * (cx - projX) + (cy - projY) * (cy - projY);
    return distSq <= radius * radius;
  }

  public List<GameItem> getActiveItems() {
    return activeItems;
  }

  public int getScore() {
    return score;
  }

  public int getCombo() {
    return combo;
  }

  public void setCombo(int combo) {
    this.combo = combo;
  }

  public int getLives() {
    return lives;
  }

  public void setLives(int lives) {
    this.lives = lives;
  }

  public boolean isGameOver() {
    return gameOver;
  }

  public float getRoundTimeRemaining() {
    return roundTimeRemaining;
  }
}
