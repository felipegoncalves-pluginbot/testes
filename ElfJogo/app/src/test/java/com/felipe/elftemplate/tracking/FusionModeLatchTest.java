package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FusionModeLatchTest {

  @Test
  public void requiresTwoGoodFramesToEnterAndFourBadToExit() {
    FusionModeLatch latch = new FusionModeLatch();
    latch.reset();
    assertFalse(latch.observe(true));
    assertTrue(latch.observe(true));
    assertTrue(latch.observe(false));
    assertTrue(latch.observe(false));
    assertTrue(latch.observe(false));
    assertFalse(latch.observe(false));
  }
}
