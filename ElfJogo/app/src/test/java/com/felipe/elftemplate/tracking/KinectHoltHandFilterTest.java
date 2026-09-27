package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Holt hand filter — limita saltos bruscos (Kinect SDK smoothing). */
public class KinectHoltHandFilterTest {

  @Test
  public void largeJumpIsClampedOnFirstFrame() {
    KinectHoltHandFilter filter = new KinectHoltHandFilter();
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.leftHand = new Joint(0.2f, 0.55f, 1500);
    result.rightHand = new Joint(0.8f, 0.55f, 1500);

    for (int i = 0; i < 6; i++) {
      filter.filterHands(result);
    }
    float stableY = result.leftHand.y;

    result.leftHand.y = 0.15f;
    filter.filterHands(result);

    assertTrue(
        "Salto bruto deve ser amortecido no 1º frame",
        Math.abs(result.leftHand.y - stableY) <= 0.13f);
    assertTrue(
        "Não deve teleportar ao raw",
        Math.abs(result.leftHand.y - 0.15f) > 0.04f);
  }

  @Test
  public void largeJumpInXIsClampedOnFirstFrame() {
    KinectHoltHandFilter filter = new KinectHoltHandFilter();
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.leftHand = new Joint(0.35f, 0.55f, 1500);
    result.rightHand = new Joint(0.65f, 0.55f, 1500);

    for (int i = 0; i < 6; i++) {
      filter.filterHands(result);
    }
    float stableX = result.rightHand.x;

    // Salto espúrio para a borda lateral da imagem
    result.rightHand.x = 0.95f;
    filter.filterHands(result);

    assertTrue(
        "Salto lateral em X deve ser amortecido no 1º frame",
        Math.abs(result.rightHand.x - stableX) <= 0.10f);
    assertTrue(
        "Não deve teleportar instantaneamente para o outlier em X",
        result.rightHand.x < 0.90f);
  }
}
