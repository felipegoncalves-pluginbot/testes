package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import org.tensorflow.lite.DataType;

public class TFLitePoseProcessorTest {

  @Test
  public void testConvertNV21ToRGBTensor_convertsWithoutCrashing() {
    int w = 640;
    int h = 480;
    byte[] yuv = new byte[w * h * 3 / 2];
    // Preenche com cinza neutro (Y=128, UV=128)
    for (int i = 0; i < yuv.length; i++) {
      yuv[i] = (byte) 128;
    }

    ByteBuffer buffer = ByteBuffer.allocateDirect(1 * 192 * 192 * 3 * 4);
    buffer.order(ByteOrder.nativeOrder());

    TFLitePoseProcessor.convertNV21ToRGBTensor(yuv, w, h, buffer, DataType.INT32);

    buffer.rewind();
    int firstR = buffer.getInt();
    int firstG = buffer.getInt();
    int firstB = buffer.getInt();

    assertTrue("R deve estar entre 120 e 136 para cinza neutro", firstR >= 120 && firstR <= 136);
    assertTrue("G deve estar entre 120 e 136 para cinza neutro", firstG >= 120 && firstG <= 136);
    assertTrue("B deve estar entre 120 e 136 para cinza neutro", firstB >= 120 && firstB <= 136);
  }

  @Test
  public void stretchNv21Luma_darkFrameIncreasesContrast() {
    int w = 64;
    int h = 48;
    byte[] yuv = new byte[w * h * 3 / 2];
    for (int i = 0; i < w * h; i++) {
      yuv[i] = (byte) 40;
    }
    yuv[0] = 20;
    yuv[4] = 60;
    TFLitePoseProcessor proc = new TFLitePoseProcessor();
    int mean = proc.stretchNv21Luma(yuv, w, h);
    assertTrue("média de cena escura < 90", mean < 90);
    int min = 255;
    int max = 0;
    for (int i = 0; i < w * h; i++) {
      int v = yuv[i] & 0xFF;
      if (v < min) {
        min = v;
      }
      if (v > max) {
        max = v;
      }
    }
    assertTrue("stretch deve ampliar o span Y", max - min >= 200);
  }

  @Test
  public void testPoseFrame_helperMethods() {
    PoseLandmarkData[] lms = new PoseLandmarkData[PoseFrame.KEYPOINT_COUNT];
    for (int i = 0; i < lms.length; i++) {
      lms[i] = new PoseLandmarkData(0.5f, 0.5f, 0.7f);
    }
    PoseFrame frame = new PoseFrame(lms, System.currentTimeMillis(), 640, 480);

    assertNotNull("PoseFrame landmarks não deve ser nulo", frame.landmarks);
    assertEquals(17, frame.landmarks.length);
    assertTrue("hasCoreKeypoints deve retornar true com score 0.7 >= 0.35", frame.hasCoreKeypoints(0.35f));
  }
}
