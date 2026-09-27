package com.felipe.elftemplate.tracking;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class KinectTrackingEngineTest {

  private KinectTrackingEngine trackingEngine;

  @Before
  public void setUp() {
    trackingEngine = new KinectTrackingEngine();
  }

  @Test
  public void testDepthSlicingExtractsPlayerCentroid() {
    int width = 64;
    int height = 48;
    short[] depthMap = new short[width * height];

    // Fill background with out-of-range depth (3500mm = 3.5m)
    for (int i = 0; i < depthMap.length; i++) {
      depthMap[i] = 3500;
    }

    // Place a player body blob at center (x: 28..36, y: 18..30) with depth 1500mm (1.5m)
    for (int y = 18; y <= 30; y++) {
      for (int x = 28; x <= 36; x++) {
        depthMap[y * width + x] = 1500;
      }
    }

    TrackingResult result =
        trackingEngine.processDepthFrame(depthMap, width, height);

    assertNotNull("Result should not be null", result);
    assertTrue("Player should be detected", result.isPlayerPresent);
    // Centroid X should be around 32 / 64 = 0.5 (normalized)
    assertEquals(0.5f, result.playerCentroidX, 0.08f);
    // Centroid Y should be around 24 / 48 = 0.5 (normalized)
    assertEquals(0.5f, result.playerCentroidY, 0.08f);
    assertEquals(1500, result.playerDistanceZ, 50);
  }

  @Test
  public void testPlayerDetectedWithArmsDownNarrowSilhouette() {
    int width = 64;
    int height = 48;
    short[] depthMap = new short[width * height];
    for (int i = 0; i < depthMap.length; i++) depthMap[i] = 3500;

    // Player standing with arms down (narrow silhouette: x: 30..34, y: 12..36)
    for (int y = 12; y <= 36; y++) {
      for (int x = 30; x <= 34; x++) {
        depthMap[y * width + x] = 1400;
      }
    }

    TrackingResult result =
        trackingEngine.processDepthFrame(depthMap, width, height);

    assertTrue("Jogador com braços abaixados não pode sumir da tela", result.isPlayerPresent);
    assertFalse("Braço esquerdo deve estar abaixado", result.isLeftHandRaised);
    assertFalse("Braço direito deve estar abaixado", result.isRightHandRaised);
  }

  @Test
  public void testHandDetectionWhenRaised() {
    int width = 64;
    int height = 48;
    short[] depthMap = new short[width * height];
    for (int i = 0; i < depthMap.length; i++) depthMap[i] = 3500;

    // Player head and body at center
    for (int y = 12; y <= 19; y++) {
      for (int x = 30; x <= 34; x++) {
        depthMap[y * width + x] = 1500;
      }
    }
    for (int y = 20; y <= 35; y++) {
      for (int x = 28; x <= 36; x++) {
        depthMap[y * width + x] = 1500;
      }
    }
    // Right arm connecting torso (x: 36, y: 20) to hand (x: 45, y: 10)
    for (int y = 10; y <= 20; y++) {
      for (int x = 36; x <= 45; x++) {
        if (x - 36 >= (20 - y) - 2 && x - 36 <= (20 - y) + 2) {
          depthMap[y * width + x] = 1480;
        }
      }
    }
    // Right hand raised high at top right (x: 45..48, y: 5..10)
    for (int y = 5; y <= 10; y++) {
      for (int x = 45; x <= 48; x++) {
        depthMap[y * width + x] = 1450;
      }
    }

    TrackingResult result =
        trackingEngine.processDepthFrame(depthMap, width, height);

    assertTrue("Player should be detected", result.isPlayerPresent);
    assertTrue("Right hand should be detected as raised", result.isRightHandRaised);
    assertTrue("Right hand X should be on the right side (>0.6)", result.rightHandX > 0.6f);
  }

  @Test
  public void testSwingGestureDetection() {
    // Feed initial frame with hand down
    trackingEngine.updateHandPosition(0.5f, 0.8f, 1000);
    // Feed second frame 100ms later with hand moved fast upwards
    KinectTrackingEngine.GestureType gesture = trackingEngine.updateHandPosition(0.52f, 0.2f, 1100);

    assertEquals(
        "Should detect SWING_UP gesture", KinectTrackingEngine.GestureType.SWING_UP, gesture);
  }

  @Test
  public void testSwipeGestureDetection() {
    // Feed initial frame with hand at right
    trackingEngine.updateHandPosition(0.8f, 0.5f, 2000);
    // Feed second frame with hand swept quickly to the left
    KinectTrackingEngine.GestureType gesture = trackingEngine.updateHandPosition(0.2f, 0.5f, 2120);

    assertEquals(
        "Should detect SWIPE_LEFT gesture", KinectTrackingEngine.GestureType.SWIPE_LEFT, gesture);
  }

  @Test
  public void testJumpAndDuckDetection() {
    int width = 64;
    int height = 48;
    short[] depthMap = new short[width * height];
    for (int i = 0; i < depthMap.length; i++) depthMap[i] = 3500;

    // Baseline body in center (y: 20..32)
    for (int y = 20; y <= 32; y++) {
      for (int x = 28; x <= 36; x++) {
        depthMap[y * width + x] = 1500;
      }
    }
    trackingEngine.processDepthFrame(depthMap, width, height);

    // Frame 2: Player jumped (body shifted up to y: 10..22)
    short[] jumpedMap = new short[width * height];
    for (int i = 0; i < jumpedMap.length; i++) jumpedMap[i] = 3500;
    for (int y = 10; y <= 22; y++) {
      for (int x = 28; x <= 36; x++) {
        jumpedMap[y * width + x] = 1500;
      }
    }
    TrackingResult jumpResult =
        trackingEngine.processDepthFrame(jumpedMap, width, height);
    assertTrue("Should detect jump", jumpResult.isJumping);
  }

  @Test
  public void testRejectInanimateChairOrTableBlob() {
    int width = 64;
    int height = 48;
    short[] depthMap = new short[width * height];
    for (int i = 0; i < depthMap.length; i++) depthMap[i] = 3500;

    // Chair blob at bottom near floor at Z=2500mm (no head, low vertical aspect, bottom heavy)
    for (int y = 30; y <= 42; y++) {
      for (int x = 16; x <= 48; x++) {
        // Hollow legs pattern
        if (y == 30 || x == 16 || x == 48 || x == 32) {
          depthMap[y * width + x] = 2500;
        }
      }
    }

    TrackingResult result =
        trackingEngine.processDepthFrame(depthMap, width, height);
    assertFalse(
        "Móvel/Cadeira no fundo não deve ser detectado como jogador", result.isPlayerPresent);
  }

  @Test
  public void testUpperBodyModeDisablesFeet() {
    int width = 64;
    int height = 48;
    short[] depthMap = new short[width * height];
    for (int i = 0; i < depthMap.length; i++) depthMap[i] = 3500;

    // Player standing close or robot head looking up (upper body only, y: 4..44 reaching bottom
    // edge)
    for (int y = 4; y <= 44; y++) {
      for (int x = 24; x <= 40; x++) {
        depthMap[y * width + x] = 1100;
      }
    }

    TrackingResult result =
        trackingEngine.processDepthFrame(depthMap, width, height);
    assertTrue("Jogador deve ser detectado", result.isPlayerPresent);
    assertFalse(
        "Pés não devem ser exibidos quando o enquadramento corta na cintura/coxas",
        result.hasFeetInFrame);
  }

  @Test
  public void testFullBodyModeEnablesFeet() {
    int width = 64;
    int height = 48;
    short[] depthMap = new short[width * height];
    for (int i = 0; i < depthMap.length; i++) depthMap[i] = 3500;

    // Full body human standing at 1.8m (y: 6..36 with clear margin from ground)
    for (int y = 6; y <= 36; y++) {
      for (int x = 28; x <= 36; x++) {
        depthMap[y * width + x] = 1800;
      }
    }

    TrackingResult result =
        trackingEngine.processDepthFrame(depthMap, width, height);
    assertTrue("Jogador de corpo inteiro deve ser detectado", result.isPlayerPresent);
    assertTrue(
        "Pés devem ser visíveis quando corpo inteiro está no enquadramento", result.hasFeetInFrame);
  }

  @Test
  public void testSeatedNearPoseIsDetected() {
    int width = 64;
    int height = 48;
    short[] depthMap = new short[width * height];
    for (int i = 0; i < depthMap.length; i++) depthMap[i] = 3500;

    // Pessoa sentada perto da câmera (~650mm), tronco largo e baixo no frame
    for (int y = 16; y <= 30; y++) {
      for (int x = 18; x <= 46; x++) {
        depthMap[y * width + x] = 650;
      }
    }

    TrackingResult result =
        trackingEngine.processDepthFrame(depthMap, width, height);

    assertTrue("Pessoa sentada perto deve ser detectada", result.isPlayerPresent);
    assertTrue("Modo proximidade deve estar ativo", result.diagnostics.isNearProximityMode);
    assertTrue("Pose sentada deve ser identificada", result.diagnostics.isSeatedPose);
    assertFalse("Pés não devem ser inferidos em pose sentada", result.hasFeetInFrame);
  }

  @Test(timeout = 500)
  public void testStopExecutesWithoutBlocking() {
    // Calling stop() should return almost instantly and not block, 
    // even if underlying resources take time to shut down.
    trackingEngine.stop();
    // Se o stop() bloquear por mais de 500ms, o timeout do JUnit falhará o teste.
    assertTrue("stop() deve retornar rapidamente", true);
  }
}
