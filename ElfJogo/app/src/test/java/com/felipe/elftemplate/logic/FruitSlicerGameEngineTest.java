package com.felipe.elftemplate.logic;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class FruitSlicerGameEngineTest {

  private FruitSlicerGameEngine engine;

  @Before
  public void setUp() {
    engine = new FruitSlicerGameEngine();
  }

  @Test
  public void testFruitSpawningAndPhysics() {
    engine.spawnTestFruit(0.5f, 0.9f, 0.0f, -0.05f, false);
    assertEquals("Should have 1 active item", 1, engine.getActiveItems().size());

    FruitSlicerGameEngine.GameItem item = engine.getActiveItems().get(0);
    float initialY = item.y;

    engine.update(1.0f); // 1 tick
    assertTrue("Item should have moved up (y decreased)", item.y < initialY);
  }

  @Test
  public void testSliceFruitIncreasesScoreAndCombo() {
    // Spawn fruit at (0.5, 0.5) with radius 0.08
    engine.spawnTestFruit(0.5f, 0.5f, 0f, 0f, false);

    int initialScore = engine.getScore();
    // Hand slices through (0.4, 0.5) -> (0.6, 0.5)
    int sliced = engine.processSlice(0.4f, 0.5f, 0.6f, 0.5f);

    assertEquals("Should slice 1 fruit", 1, sliced);
    assertTrue("Score should increase", engine.getScore() > initialScore);
    assertEquals("Combo should be 1", 1, engine.getCombo());
    assertTrue("Fruit should be marked sliced", engine.getActiveItems().get(0).isSliced);
  }

  @Test
  public void testSliceBombDecreasesLivesAndResetsCombo() {
    engine.spawnTestFruit(0.5f, 0.5f, 0f, 0f, true); // Bomb
    engine.setCombo(5);
    int initialLives = engine.getLives();

    int sliced = engine.processSlice(0.4f, 0.5f, 0.6f, 0.5f);

    assertEquals("Should trigger 1 bomb", 1, sliced);
    assertEquals("Combo should be reset to 0", 0, engine.getCombo());
    assertEquals("Lives should decrease by 1", initialLives - 1, engine.getLives());
  }

  @Test
  public void testGameOverWhenLivesReachZero() {
    engine.setLives(1);
    engine.spawnTestFruit(0.5f, 0.5f, 0f, 0f, true); // Bomb
    engine.processSlice(0.4f, 0.5f, 0.6f, 0.5f);

    assertTrue("Game should be over", engine.isGameOver());
  }
}
