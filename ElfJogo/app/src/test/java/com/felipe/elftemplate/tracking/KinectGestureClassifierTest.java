package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/** Testes unitários para classificação de gestos e histerese Schmitt-Trigger dos braços. */
public class KinectGestureClassifierTest {

  private KinectGestureClassifier classifier;

  @Before
  public void setUp() {
    classifier = new KinectGestureClassifier();
  }

  @Test
  public void testArmHysteresisSchmittTrigger() {
    assertFalse(classifier.isLeftArmRaised());

    for (int i = 0; i < 4; i++) {
      classifier.updateLeftArmState(0.35f, 0.50f, true);
    }
    assertTrue(classifier.isLeftArmRaised());

    classifier.updateLeftArmState(0.52f, 0.50f, true);
    assertTrue(classifier.isLeftArmRaised());

    try {
      Thread.sleep(220);
    } catch (InterruptedException ignored) {}

    for (int i = 0; i < 8; i++) {
      classifier.updateLeftArmState(0.90f, 0.50f, true);
    }
    assertFalse(classifier.isLeftArmRaised());
  }

  @Test
  public void testDuckAndJumpClassification() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.head.y = 0.85f; // Cabeça muito baixa (agachado)
    result.spine.y = 0.70f;

    classifier.classifyPostures(result, 0.35f);
    assertTrue(result.isDucking);
    assertFalse(result.isJumping);
  }
}
