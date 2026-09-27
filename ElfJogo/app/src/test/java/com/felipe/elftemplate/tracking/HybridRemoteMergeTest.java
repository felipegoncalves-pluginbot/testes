package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class HybridRemoteMergeTest {

  @Test
  public void rejectsRemoteWithoutPlayerPresent() {
    TrackingResult remote = new TrackingResult();
    remote.reset();
    FakeRelay relay = new FakeRelay(true);
    assertFalse(HybridRemoteMerge.isRemoteUsable(relay, System.currentTimeMillis(), remote));
  }

  @Test
  public void armsOnlyKeepsLocalSkeletonAndRemoteElevation() {
    TrackingResult local = buildLocal();
    TrackingResult remote = buildRemote();
    short[] depth = flatDepth(64, 48, 1500);

    TrackingResult out = new TrackingResult();
    boolean ok = HybridRemoteMerge.applyRemoteExtension(out, local, remote, depth, 64, 48);

    assertTrue(ok);
    assertEquals(0.48f, out.head.x, 0.001f);
    assertEquals(1400, out.head.z);
    assertEquals(0.7f, out.leftHandElevation, 0.001f);
    assertEquals(28f, out.diagnostics.cameraFps, 0.01f);
    assertTrue(out.diagnostics.isRemoteAuxActive);
    assertEquals(0.35f, out.leftHand.x, 0.001f);
    assertEquals(1500, out.leftHand.z);
  }

  private static TrackingResult buildLocal() {
    TrackingResult local = new TrackingResult();
    local.isPlayerPresent = true;
    local.playerCentroidX = 0.5f;
    local.head.set(0.48f, 0.25f, 1400);
    local.leftShoulder.set(0.45f, 0.32f, 1400);
    local.rightShoulder.set(0.55f, 0.32f, 1400);
    local.leftHand.set(0.42f, 0.55f, 1400);
    local.diagnostics.cameraFps = 28f;
    return local;
  }

  private static TrackingResult buildRemote() {
    TrackingResult remote = new TrackingResult();
    remote.isPlayerPresent = true;
    remote.head.set(0.9f, 0.1f, 0);
    remote.leftShoulder.set(0.45f, 0.3f, 0);
    remote.rightShoulder.set(0.55f, 0.3f, 0);
    remote.leftElbow.set(0.40f, 0.42f, 0);
    remote.leftHand.set(0.35f, 0.28f, 0);
    remote.leftHandElevation = 0.7f;
    return remote;
  }

  private static short[] flatDepth(int w, int h, int mm) {
    short[] depth = new short[w * h];
    Arrays.fill(depth, (short) mm);
    return depth;
  }

  private static final class FakeRelay implements MirrorTrackingRelay {
    private final boolean connected;

    FakeRelay(boolean connected) {
      this.connected = connected;
    }

    @Override
    public void start(int port) {}

    @Override
    public void stop() {}

    @Override
    public boolean isAuxiliaryConnected() {
      return connected;
    }

    @Override
    public long getLastRemoteAgeMs() {
      return 0L;
    }

    @Override
    public void setRemoteResultListener(RemoteResultListener listener) {}

    @Override
    public void offerRgbFrame(byte[] nv21, int width, int height) {}

    @Override
    public void offerDepthFrame(short[] depthMm, int width, int height) {}
  }
}
