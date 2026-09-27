package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LatestEncodedSlotTest {

  @Test
  public void copiesPayloadAndClearsPending() {
    LatestEncodedSlot slot = new LatestEncodedSlot();
    slot.offer(new byte[] {1, 2, 3});
    byte[] dest = new byte[8];
    boolean[] dropped = new boolean[1];
    int n = slot.copyTo(dest, dropped);
    assertEquals(3, n);
    assertFalse(dropped[0]);
    assertEquals(1, dest[0]);
    assertEquals(3, dest[2]);
    assertEquals(0, slot.copyTo(dest, dropped));
  }

  @Test
  public void keepsLatestAndFlagsDrop() {
    com.felipe.elftemplate.tracking.LatestEncodedSlot slot =
        new com.felipe.elftemplate.tracking.LatestEncodedSlot();
    for (int i = 0; i < 3; i++) {
      slot.offer(new byte[] {(byte) i, 9});
    }
    slot.offer(new byte[] {4, 5, 6});
    byte[] dest = new byte[8];
    boolean[] dropped = new boolean[1];
    int n = slot.copyTo(dest, dropped);
    assertEquals(3, n);
    assertTrue(dropped[0]);
    assertArrayEquals(new byte[] {4, 5, 6}, new byte[] {dest[0], dest[1], dest[2]});
  }

  @Test
  public void shortDestDoesNotConsume() {
    LatestEncodedSlot slot = new LatestEncodedSlot();
    slot.offer(new byte[] {1, 2, 3, 4});
    boolean[] dropped = new boolean[1];
    int n = slot.copyTo(new byte[2], dropped);
    assertEquals(-4, n);
    byte[] dest = new byte[4];
    n = slot.copyTo(dest, dropped);
    assertEquals(4, n);
    assertEquals(4, dest[3]);
  }
}
