package com.felipe.elftemplate.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class CprTrainingEngineTest {

  private CprTrainingEngine engine;

  @Before
  public void setUp() {
    engine = new CprTrainingEngine();
  }

  @Test
  public void testStartsAtSafetyStep() {
    assertEquals(CprStep.SAFETY, engine.getCurrentStep());
    assertEquals(0, engine.getStepIndex());
    assertTrue(!engine.getCurrentStep().getEmoji().isEmpty());
  }

  @Test
  public void testAdvanceAndRetreatSteps() {
    assertTrue(engine.advanceStep());
    assertEquals(CprStep.RESPONSIVENESS, engine.getCurrentStep());

    assertTrue(engine.previousStep());
    assertEquals(CprStep.SAFETY, engine.getCurrentStep());
  }

  @Test
  public void testAutoCountsCompressionsOnBeatCycle() {
    engine.goToStep(4);
    float beat = 60f / CprTrainingEngine.TARGET_BPM;
    engine.update(beat, true);
    assertEquals(0, engine.getCompressionCount());

    engine.update(beat, true);
    assertEquals(1, engine.getCompressionCount());
  }

  @Test
  public void testPulseAdvancesAtTargetBpm() {
    engine.goToStep(4);
    float halfBeat = (60f / CprTrainingEngine.TARGET_BPM) / 2f;
    engine.update(halfBeat, true);
    assertTrue(engine.getPulseScale() > 1f);

    boolean phaseChanged = engine.update(halfBeat + 0.01f, true);
    assertTrue(phaseChanged);
    assertFalse(engine.isCompressPhase());
  }
}
