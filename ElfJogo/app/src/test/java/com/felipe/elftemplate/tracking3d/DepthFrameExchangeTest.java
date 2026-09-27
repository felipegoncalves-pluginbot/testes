package com.felipe.elftemplate.tracking3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class DepthFrameExchangeTest {

  private static final int WIDTH = 4;
  private static final int HEIGHT = 3;

  private static short[] frameFilledWith(int value) {
    short[] depth = new short[WIDTH * HEIGHT];
    Arrays.fill(depth, (short) value);
    return depth;
  }

  @Test
  public void takeReturnsTheNewestFrameAndDropsIntermediates() {
    DepthFrameExchange exchange = new DepthFrameExchange();
    exchange.publish(frameFilledWith(1000), WIDTH, HEIGHT);
    exchange.publish(frameFilledWith(1100), WIDTH, HEIGHT);
    exchange.publish(frameFilledWith(1200), WIDTH, HEIGHT);

    DepthFrameExchange.Frame frame = exchange.take();
    assertEquals("frame mais recente", 1200, frame.data[0]);
    assertEquals(WIDTH, frame.width);
    assertEquals(HEIGHT, frame.height);
    assertNull("nada novo depois de consumir", exchange.take());
  }

  /** No buffer duplo antigo, o terceiro frame publicado caía no array que o worker ainda lia. */
  @Test
  public void framesPublishedDuringProcessingNeverTouchTheFrameInUse() {
    DepthFrameExchange exchange = new DepthFrameExchange();
    exchange.publish(frameFilledWith(1500), WIDTH, HEIGHT);
    DepthFrameExchange.Frame inUse = exchange.take();

    for (int value = 1600; value <= 2000; value += 100) {
      exchange.publish(frameFilledWith(value), WIDTH, HEIGHT);
    }

    for (short depth : inUse.data) {
      assertEquals("frame em processamento não pode mudar", 1500, depth);
    }
    assertEquals("o próximo take entrega o último publicado", 2000, exchange.take().data[0]);
  }

  @Test
  public void hasUnreadReflectsWhetherTheWorkerIsBehind() {
    DepthFrameExchange exchange = new DepthFrameExchange();
    assertFalse("vazio no início", exchange.hasUnread());

    exchange.publish(frameFilledWith(900), WIDTH, HEIGHT);
    assertTrue("frame publicado e não lido", exchange.hasUnread());

    exchange.take();
    assertFalse("nada pendente depois do take", exchange.hasUnread());
  }
}
