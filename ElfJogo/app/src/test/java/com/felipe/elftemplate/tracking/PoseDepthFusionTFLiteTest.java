package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Testes TDD para fusão do MoveNet TFLite (17 keypoints) com mapa de profundidade 3D Astra.
 */
public class PoseDepthFusionTFLiteTest {

  private short[] depthData;
  private int width;
  private int height;

  @Before
  public void setUp() {
    width = 640;
    height = 480;
    depthData = new short[width * height];
    for (int i = 0; i < depthData.length; i++) {
      depthData[i] = (short) 1500; // 1.5m constante
    }
  }

  @Test
  public void testFuseValidMoveNetPoseFrameProduces3DSkeleton() {
    PoseLandmarkData[] lms = new PoseLandmarkData[PoseFrame.KEYPOINT_COUNT];
    for (int i = 0; i < lms.length; i++) {
      lms[i] = new PoseLandmarkData(0.5f, 0.5f, 0.85f);
    }
    lms[PoseFrame.NOSE] = new PoseLandmarkData(0.50f, 0.20f, 0.90f);
    lms[PoseFrame.RIGHT_SHOULDER] = new PoseLandmarkData(0.40f, 0.35f, 0.85f);
    lms[PoseFrame.LEFT_SHOULDER] = new PoseLandmarkData(0.60f, 0.35f, 0.85f);
    lms[PoseFrame.RIGHT_ELBOW] = new PoseLandmarkData(0.30f, 0.45f, 0.80f);
    lms[PoseFrame.LEFT_ELBOW] = new PoseLandmarkData(0.70f, 0.45f, 0.80f);
    lms[PoseFrame.RIGHT_WRIST] = new PoseLandmarkData(0.20f, 0.25f, 0.75f);
    lms[PoseFrame.LEFT_WRIST] = new PoseLandmarkData(0.80f, 0.55f, 0.75f);
    lms[PoseFrame.RIGHT_HIP] = new PoseLandmarkData(0.45f, 0.65f, 0.80f);
    lms[PoseFrame.LEFT_HIP] = new PoseLandmarkData(0.55f, 0.65f, 0.80f);
    lms[PoseFrame.RIGHT_KNEE] = new PoseLandmarkData(0.42f, 0.80f, 0.80f);
    lms[PoseFrame.LEFT_KNEE] = new PoseLandmarkData(0.58f, 0.80f, 0.80f);
    lms[PoseFrame.RIGHT_ANKLE] = new PoseLandmarkData(0.40f, 0.95f, 0.75f);
    lms[PoseFrame.LEFT_ANKLE] = new PoseLandmarkData(0.60f, 0.95f, 0.75f);

    long now = System.currentTimeMillis();
    PoseFrame frame = new PoseFrame(lms, now, 640, 480);
    TrackingResult result = new TrackingResult();

    boolean fused = PoseDepthFusion.tryFuse(result, frame, depthData, width, height, now);

    assertTrue("Fusão deve ser bem sucedida", fused);
    assertTrue("Player deve estar marcado como presente", result.isPlayerPresent);
    assertEquals(0.50f, result.head.x, 0.01f);
    assertEquals(0.20f, result.head.y, 0.01f);
    assertEquals(1500, result.head.z);
    assertEquals(0.50f, result.neck.x, 0.01f);
    assertEquals(0.35f, result.neck.y, 0.01f);
    assertEquals(0.20f, result.leftHand.x, 0.01f);
    assertEquals(0.25f, result.leftHand.y, 0.01f);
    assertEquals(0.80f, result.rightHand.x, 0.01f);
    assertEquals(0.55f, result.rightHand.y, 0.01f);

    // Verificação de membros inferiores
    assertTrue("hasLegsInFrame deve ser verdadeiro", result.hasLegsInFrame);
    assertTrue("hasFeetInFrame deve ser verdadeiro", result.hasFeetInFrame);
    assertEquals(0.42f, result.leftKnee.x, 0.01f);
    assertEquals(0.80f, result.leftKnee.y, 0.01f);
    assertEquals(0.58f, result.rightKnee.x, 0.01f);
    assertEquals(0.80f, result.rightKnee.y, 0.01f);
    assertEquals(0.40f, result.leftFoot.x, 0.01f);
    assertEquals(0.95f, result.leftFoot.y, 0.01f);
    assertEquals(0.60f, result.rightFoot.x, 0.01f);
    assertEquals(0.95f, result.rightFoot.y, 0.01f);
  }

  @Test
  public void testStaleMoveNetPoseFrameIsRejected() {
    PoseLandmarkData[] lms = new PoseLandmarkData[PoseFrame.KEYPOINT_COUNT];
    for (int i = 0; i < lms.length; i++) {
      lms[i] = new PoseLandmarkData(0.5f, 0.5f, 0.9f);
    }
    long now = System.currentTimeMillis();
    PoseFrame staleFrame = new PoseFrame(lms, now - 1200, 640, 480);
    TrackingResult result = new TrackingResult();

    boolean fused = PoseDepthFusion.tryFuse(result, staleFrame, depthData, width, height, now);

    assertFalse("Frame desatualizado deve ser rejeitado", fused);
  }

  @Test
  public void testLowConfidenceCoreKeypointsRejected() {
    PoseLandmarkData[] lms = new PoseLandmarkData[PoseFrame.KEYPOINT_COUNT];
    for (int i = 0; i < lms.length; i++) {
      lms[i] = new PoseLandmarkData(0.5f, 0.5f, 0.1f);
    }
    long now = System.currentTimeMillis();
    PoseFrame lowConfFrame = new PoseFrame(lms, now, 640, 480);
    TrackingResult result = new TrackingResult();

    boolean fused = PoseDepthFusion.tryFuse(result, lowConfFrame, depthData, width, height, now);

    assertFalse("Frame com baixa confiança deve ser rejeitado", fused);
  }

  @Test
  public void testTooDarkRgbIsRejected() {
    PoseLandmarkData[] lms = new PoseLandmarkData[PoseFrame.KEYPOINT_COUNT];
    for (int i = 0; i < lms.length; i++) {
      lms[i] = new PoseLandmarkData(0.50f, 0.40f, 0.90f);
    }
    lms[PoseFrame.NOSE] = new PoseLandmarkData(0.50f, 0.20f, 0.90f);
    lms[PoseFrame.RIGHT_SHOULDER] = new PoseLandmarkData(0.40f, 0.35f, 0.85f);
    lms[PoseFrame.LEFT_SHOULDER] = new PoseLandmarkData(0.60f, 0.35f, 0.85f);
    PoseFrame frame = new PoseFrame(lms, System.currentTimeMillis(), 640, 480);
    frame.setLuma(12, 8);
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.playerCentroidX = 0.50f;

    boolean fused =
        PoseDepthFusion.tryFuse(result, frame, depthData, width, height, frame.timestampMs);

    assertFalse("Luma baixa (luz ruim) deve recusar fusão MoveNet", fused);
  }

  @Test
  public void testSpatialInconsistencyBetweenRGBAndDepthIsRejected() {
    // Cenário onde RGB MoveNet detecta cabeça em X=0.20 mas jogador de profundidade está em X=0.75
    PoseLandmarkData[] lms = new PoseLandmarkData[PoseFrame.KEYPOINT_COUNT];
    for (int i = 0; i < lms.length; i++) {
      lms[i] = new PoseLandmarkData(0.20f, 0.5f, 0.85f);
    }
    lms[PoseFrame.NOSE] = new PoseLandmarkData(0.20f, 0.20f, 0.90f);
    lms[PoseFrame.RIGHT_SHOULDER] = new PoseLandmarkData(0.15f, 0.35f, 0.85f);
    lms[PoseFrame.LEFT_SHOULDER] = new PoseLandmarkData(0.25f, 0.35f, 0.85f);

    long now = System.currentTimeMillis();
    PoseFrame frame = new PoseFrame(lms, now, 640, 480);
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.playerCentroidX = 0.75f; // Jogador no lado direito

    boolean fused = PoseDepthFusion.tryFuse(result, frame, depthData, width, height, now);

    assertFalse("Detecção RGB descolada do corpo de profundidade deve ser rejeitada", fused);
  }
}
