package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/** Testes unitários para segmentação de profundidade, histograma e histerese de proximidade. */
public class DepthHistogramSegmenterTest {

  private DepthHistogramSegmenter segmenter;

  @Before
  public void setUp() {
    segmenter = new DepthHistogramSegmenter();
  }

  @Test
  public void testMedianDepthCalculation() {
    int[] samples = {1200, 1500, 1100, 1900, 1300};
    int median = DepthHistogramSegmenter.calculateMedian(samples, 5);
    assertEquals(1300, median);
  }

  @Test
  public void testProximityLatchHysteresis() {
    assertFalse(segmenter.isProximityMode());

    // Entra no modo de proximidade (< 950mm)
    segmenter.updateProximityLatch(800);
    assertTrue(segmenter.isProximityMode());

    // Permanece no modo de proximidade mesmo a 1000mm (histerese até 1100mm)
    segmenter.updateProximityLatch(1000);
    assertTrue(segmenter.isProximityMode());

    // Sai do modo de proximidade (> 1100mm)
    segmenter.updateProximityLatch(1200);
    assertFalse(segmenter.isProximityMode());
  }
}
