package com.felipe.elftemplate.tracking;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class HeadGestureDetectorTest {

  private HeadGestureDetector detector;

  @Before
  public void setUp() {
    detector = new HeadGestureDetector();
  }

  @Test
  public void testDetectHeadShakeNo() {
    long time = 1000;
    // Simula movimento rítmico horizontal (Esquerda -> Direita -> Esquerda -> Direita)
    // Sem movimento vertical significativo (Y estável em 0.20)
    float[] xSequence = {0.50f, 0.45f, 0.40f, 0.46f, 0.54f, 0.60f, 0.52f, 0.42f, 0.48f, 0.58f};
    KinectTrackingEngine.GestureType result = KinectTrackingEngine.GestureType.IDLE;

    for (float x : xSequence) {
      result = detector.addSample(x, 0.20f, time);
      time += 70; // ~14 FPS de amostras de cabeça
      if (result == KinectTrackingEngine.GestureType.HEAD_SHAKE_NO) break;
    }

    assertEquals(
        "Deve detectar gesto de balanço de cabeça NÃO",
        KinectTrackingEngine.GestureType.HEAD_SHAKE_NO,
        result);
  }

  @Test
  public void testDetectHeadNodYes() {
    long time = 1000;
    // Simula movimento rítmico vertical (Baixo -> Cima -> Baixo -> Cima)
    // Sem movimento horizontal significativo (X estável em 0.50)
    float[] ySequence = {0.20f, 0.25f, 0.30f, 0.24f, 0.16f, 0.12f, 0.20f, 0.28f, 0.22f, 0.14f};
    KinectTrackingEngine.GestureType result = KinectTrackingEngine.GestureType.IDLE;

    for (float y : ySequence) {
      result = detector.addSample(0.50f, y, time);
      time += 70;
      if (result == KinectTrackingEngine.GestureType.HEAD_NOD_YES) break;
    }

    assertEquals(
        "Deve detectar gesto de aceno de cabeça SIM",
        KinectTrackingEngine.GestureType.HEAD_NOD_YES,
        result);
  }

  @Test
  public void testStaticHeadReturnsIdle() {
    long time = 1000;
    KinectTrackingEngine.GestureType result = KinectTrackingEngine.GestureType.IDLE;

    for (int i = 0; i < 10; i++) {
      result = detector.addSample(0.50f, 0.20f, time);
      time += 70;
      assertNotNull(result);
    }

    assertEquals(
        "Cabeça estática deve retornar IDLE", KinectTrackingEngine.GestureType.IDLE, result);
  }
}
