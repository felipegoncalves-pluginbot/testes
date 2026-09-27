package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

/**
 * Regressão do pipeline híbrido Kinect-style: sem celular = 100% local; com celular = só braços.
 */
public class HybridKinectRegressionTest {

  private FakeRelay relay;
  private HybridTrackingProvider hybrid;
  private TrackingResult delivered;
  private final TrackingResult local = new TrackingResult();
  private final TrackingResult remote = new TrackingResult();

  @Before
  public void setUp() {
    relay = new FakeRelay();
    hybrid = new HybridTrackingProvider(new LocalKinectTrackingProvider(), relay);
    hybrid.bindTestCallback((r, s) -> delivered = r);
    hybrid.bindDepthForTest(flatDepth(64, 48, 1500), 64, 48);
    seedLocal();
    seedRemote();
  }

  @Test
  public void withoutPhoneIsIdenticalToLocalPipeline() {
    hybrid.ingestLocalFrameForTest(local, null);
    assertEquals(0.48f, delivered.head.x, 0.001f);
    assertEquals(0.2f, delivered.leftHandElevation, 0.001f);
    assertFalse(delivered.diagnostics.isRemoteAuxActive);
  }

  @Test
  public void disconnectedRelayNeverFlagsRemoteAux() {
    relay.setConnected(true);
    relay.deliverRemote(1L, remote, System.currentTimeMillis());
    relay.setConnected(false);
    hybrid.ingestLocalFrameForTest(local, null);
    assertFalse(delivered.diagnostics.isRemoteAuxActive);
    assertEquals(0.2f, delivered.leftHandElevation, 0.001f);
  }

  @Test
  public void mirrorPoseStreamSkipsHdCrop() {
    assertTrue(MirrorRgbDepthAlign.isFourThree(640, 480));
    int[] rect = MirrorRgbDepthAlign.cropFourThreeRect(640, 480);
    assertEquals(0, rect[0]);
    assertEquals(640, rect[2]);
    assertEquals(480, rect[3]);
  }

  @Test
  public void remoteBadHeadNeverReplacesLocalHead() {
    relay.setConnected(true);
    remote.head.set(0.99f, 0.01f, 0);
    remote.leftHand.set(0.35f, 0.28f, 0);
    relay.deliverRemote(2L, remote, System.currentTimeMillis());
    hybrid.ingestLocalFrameForTest(local, null);
    assertEquals(0.48f, delivered.head.x, 0.001f);
    assertTrue(delivered.diagnostics.isRemoteAuxActive);
  }

  private void seedLocal() {
    local.reset();
    local.isPlayerPresent = true;
    local.leftHandElevation = 0.2f;
    local.head.set(0.48f, 0.25f, 1400);
    local.leftShoulder.set(0.45f, 0.32f, 1400);
    local.rightShoulder.set(0.55f, 0.32f, 1400);
    local.leftHand.set(0.42f, 0.55f, 1400);
  }

  private void seedRemote() {
    remote.reset();
    remote.isPlayerPresent = true;
    remote.leftHandElevation = 0.85f;
    remote.head.set(0.5f, 0.2f, 0);
    remote.leftShoulder.set(0.45f, 0.3f, 0);
    remote.rightShoulder.set(0.55f, 0.3f, 0);
    remote.leftHand.set(0.35f, 0.28f, 0);
    remote.leftElbow.set(0.40f, 0.42f, 0);
  }

  private static short[] flatDepth(int w, int h, int mm) {
    short[] depth = new short[w * h];
    Arrays.fill(depth, (short) mm);
    return depth;
  }

  private static final class FakeRelay implements MirrorTrackingRelay {
    private RemoteResultListener listener;
    private boolean connected;

    @Override
    public void start(int port) {}

    @Override
    public void stop() {
      connected = false;
    }

    @Override
    public boolean isAuxiliaryConnected() {
      return connected;
    }

    @Override
    public long getLastRemoteAgeMs() {
      return 0L;
    }

    @Override
    public void setRemoteResultListener(RemoteResultListener listener) {
      this.listener = listener;
    }

    @Override
    public void offerRgbFrame(byte[] nv21, int width, int height) {}

    @Override
    public void offerDepthFrame(short[] depthMm, int width, int height) {}

    void setConnected(boolean connected) {
      this.connected = connected;
    }

    void deliverRemote(long seq, TrackingResult result, long timestampMs) {
      if (listener != null) {
        listener.onRemoteTrack(seq, result, timestampMs);
      }
    }
  }
}
