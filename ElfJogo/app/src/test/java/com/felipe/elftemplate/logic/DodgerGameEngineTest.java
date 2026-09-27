package com.felipe.elftemplate.logic;

import static org.junit.Assert.*;

import com.felipe.elftemplate.logic.DodgerGameEngine;
import org.junit.Before;
import org.junit.Test;

public class DodgerGameEngineTest {

  private DodgerGameEngine engine;

  @Before
  public void setUp() {
    engine = new DodgerGameEngine();
  }

  @Test
  public void testObstacleMovementAndPlayerDodge() {
    for (int i = 0; i < 3; i++) {
      engine.spawnObstacle(DodgerGameEngine.ObstacleType.LOW_HURDLE, 0.5f, 0.8f);
    }
    assertNotNull(engine);

    // Player is at x=0.5, but JUMPING (isJumping = true)
    boolean hit = engine.checkCollision(0.5f, true, false);
    assertFalse("Jumping player should clear low hurdle", hit);

    // Player is at x=0.5, NOT jumping
    boolean hitStanding = engine.checkCollision(0.5f, false, false);
    assertTrue("Standing player should hit low hurdle", hitStanding);
  }

  @Test
  public void testHighObstacleDuck() {
    // Spawn overhead laser requiring duck
    engine.spawnObstacle(DodgerGameEngine.ObstacleType.HIGH_BARRIER, 0.5f, 0.8f);

    // Player ducks
    boolean hitDucking = engine.checkCollision(0.5f, false, true);
    assertFalse("Ducking player should clear high barrier", hitDucking);

    // Player standing
    boolean hitStanding = engine.checkCollision(0.5f, false, false);
    assertTrue("Standing player should hit high barrier", hitStanding);
  }

  @Test
  public void testSideObstacleLeaning() {
    // Obstacle on left lane (x: 0.2)
    engine.spawnObstacle(DodgerGameEngine.ObstacleType.LEFT_BLOCK, 0.2f, 0.8f);

    // Player leans right (x: 0.8)
    boolean hit = engine.checkCollision(0.8f, false, false);
    assertFalse("Player on right should avoid left block", hit);

    // Player on left (x: 0.2)
    boolean hitLeft = engine.checkCollision(0.2f, false, false);
    assertTrue("Player on left should hit left block", hitLeft);
  }
}
