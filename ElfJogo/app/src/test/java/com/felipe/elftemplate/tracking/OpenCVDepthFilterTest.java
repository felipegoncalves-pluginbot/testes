package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.felipe.elftemplate.tracking.OpenCVDepthFilter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;

public class OpenCVDepthFilterTest {

  @Test
  public void testFilterDepthHandlesNullBufferSafely() {
    boolean res = OpenCVDepthFilter.filterDepth(null, 640, 480, OpenCVDepthFilter.MODE_MEDIAN_3X3);
    assertFalse("Null buffer deve retornar false sem crash", res);
  }

  @Test
  public void testFilterDepthHandlesInvalidDimensionsSafely() {
    int w = 640;
    int h = 480;
    short[] depthMap = new short[w * h];
    for (int i = 0; i < depthMap.length; i++) {
      depthMap[i] = (short) 1200;
    }

    ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 2);
    buffer.order(ByteOrder.nativeOrder());
    for (int i = 0; i < depthMap.length; i++) {
      buffer.putShort(depthMap[i]);
    }

    boolean res = OpenCVDepthFilter.filterDepth(buffer, 0, 0, OpenCVDepthFilter.MODE_MEDIAN_3X3);
    assertFalse("Dimensões inválidas devem retornar false", res);
    assertEquals("MODE_MEDIAN_3X3 deve ser 0", 0, OpenCVDepthFilter.MODE_MEDIAN_3X3);
    assertEquals("MODE_MORPH_CLOSE deve ser 1", 1, OpenCVDepthFilter.MODE_MORPH_CLOSE);
  }
}
