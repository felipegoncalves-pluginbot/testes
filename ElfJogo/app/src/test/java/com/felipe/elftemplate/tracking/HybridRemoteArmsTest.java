package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

/** Regressão: aux só braços; esqueleto local intacto (Kinect depth-first). */
public class HybridRemoteArmsTest {

  @Test
  public void preservesLocalHeadWhenRemoteHeadIsWrong() {
    TrackingResult local = baseLocal();
    TrackingResult remote = baseRemote();
    remote.head.set(0.95f, 0.05f, 0);

    TrackingResult out = new TrackingResult();
    assertTrue(HybridRemoteArms.applyArmsExtension(out, local, remote, flatDepth(64, 48, 1500), 64, 48));

    assertEquals(0.48f, out.head.x, 0.001f);
    assertEquals(1400, out.head.z);
    assertEquals(0.7f, out.leftHandElevation, 0.001f);
  }

  @Test
  public void rejectsRemoteHandOutsideSpatialGate() {
    TrackingResult local = baseLocal();
    TrackingResult remote = baseRemote();
    remote.leftHand.set(0.05f, 0.30f, 0);

    TrackingResult out = new TrackingResult();
    HybridRemoteArms.applyArmsExtension(out, local, remote, flatDepth(64, 48, 1500), 64, 48);

    assertEquals(0.42f, out.leftHand.x, 0.001f);
    assertEquals(0.7f, out.leftHandElevation, 0.001f);
  }

  @Test
  public void appliesRemoteHandWithHostDepthZ() {
    TrackingResult local = baseLocal();
    TrackingResult remote = baseRemote();

    TrackingResult out = new TrackingResult();
    HybridRemoteArms.applyArmsExtension(out, local, remote, flatDepth(64, 48, 1500), 64, 48);

    assertEquals(0.35f, out.leftHand.x, 0.001f);
    assertEquals(1500, out.leftHand.z);
  }

  @Test
  public void failsWithoutLocalPlayer() {
    TrackingResult local = new TrackingResult();
    local.reset();
    TrackingResult remote = baseRemote();
    TrackingResult out = new TrackingResult();
    assertFalse(
        HybridRemoteArms.applyArmsExtension(out, local, remote, flatDepth(64, 48, 1500), 64, 48));
  }

  private static TrackingResult baseLocal() {
    TrackingResult local = new TrackingResult();
    local.isPlayerPresent = true;
    local.head.set(0.48f, 0.25f, 1400);
    local.leftShoulder.set(0.45f, 0.32f, 1400);
    local.rightShoulder.set(0.55f, 0.32f, 1400);
    local.leftHand.set(0.42f, 0.55f, 1400);
    return local;
  }

  private static TrackingResult baseRemote() {
    TrackingResult remote = new TrackingResult();
    remote.isPlayerPresent = true;
    remote.leftHandElevation = 0.7f;
    remote.leftElbow.set(0.40f, 0.42f, 0);
    remote.leftHand.set(0.35f, 0.28f, 0);
    return remote;
  }

  private static short[] flatDepth(int w, int h, int mm) {
    short[] depth = new short[w * h];
    Arrays.fill(depth, (short) mm);
    return depth;
  }
}
