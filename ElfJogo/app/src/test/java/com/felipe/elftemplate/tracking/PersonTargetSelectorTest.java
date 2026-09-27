package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

/** Testes do seletor de alvo (closest / sticky / center). */
public class PersonTargetSelectorTest {

  private PersonTargetSelector selector;
  private TrackingModeConfig config;

  @Before
  public void setUp() {
    selector = new PersonTargetSelector();
    config = new TrackingModeConfig();
  }

  @Test
  public void closestModePicksNearestPerson() {
    config.setMode(TrackingMode.SINGLE_CLOSEST);
    PersonBlob near = blob(1, 0.5f, 0.5f, 900);
    PersonBlob far = blob(2, 0.5f, 0.5f, 1800);

    PersonBlob picked = selector.selectPrimary(java.util.Arrays.asList(far, near), config);
    assertEquals(900, picked.distanceZ);
    assertEquals(1, picked.label);
  }

  @Test
  public void stickyModeKeepsLockedPlayer() {
    config.setMode(TrackingMode.SINGLE_STICKY);
    PersonBlob first = blob(5, 0.4f, 0.5f, 1200);
    selector.selectPrimary(java.util.Arrays.asList(first), config);

    PersonBlob closer = blob(9, 0.5f, 0.5f, 800);
    PersonBlob picked = selector.selectPrimary(java.util.Arrays.asList(closer, first), config);
    assertEquals(5, picked.label);
  }

  @Test
  public void prefersPersonOverCloserSmallFurnitureBlob() {
    config.setMode(TrackingMode.SINGLE_CLOSEST);
    PersonBlob chair = new PersonBlob(1, 4, 12, 20, 30, 0.2f, 0.6f, 800, 28, 0.9f, 0.4f, false, false);
    PersonBlob person =
        new PersonBlob(2, 20, 50, 8, 45, 0.5f, 0.5f, 1500, 90, 1.3f, 0.5f, false, false);
    PersonBlob picked = selector.selectPrimary(java.util.Arrays.asList(chair, person), config);
    assertEquals("fantasma de cadeira não pode ganhar do jogador", 2, picked.label);
  }

  @Test
  public void centerModePicksMiddleOfFrame() {
    config.setMode(TrackingMode.SINGLE_CENTER);
    PersonBlob left = blob(1, 0.2f, 0.5f, 1000);
    PersonBlob center = blob(2, 0.52f, 0.5f, 1100);

    PersonBlob picked = selector.selectPrimary(java.util.Arrays.asList(left, center), config);
    assertEquals(2, picked.label);
  }

  private static PersonBlob blob(int label, float cx, float cy, int z) {
    return new PersonBlob(label, 10, 50, 10, 50, cx, cy, z, 80, 1.2f, 0.5f, false, false);
  }
}
