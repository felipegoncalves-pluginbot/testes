package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

/** Fusão remota Kinect-style: braços do aux; corpo local; ignora track vazio. */
public class HybridTrackingProviderTest {

  private FakeRelay relay;
  private HybridTrackingProvider hybrid;
  private TrackingResult lastDelivered;
  private final TrackingResult localSample = new TrackingResult();
  private final TrackingResult remoteSample = new TrackingResult();

  @Before
  public void setUp() {
    relay = new FakeRelay();
    hybrid = new HybridTrackingProvider(new LocalKinectTrackingProvider(), relay);
    hybrid.bindTestCallback((result, silhouette) -> lastDelivered = result);
    hybrid.bindDepthForTest(flatDepth(64, 48, 1500), 64, 48);
    localSample.reset();
    remoteSample.reset();
    localSample.isPlayerPresent = true;
    localSample.playerCentroidX = 0.5f;
    localSample.leftHandElevation = 0.2f;
    localSample.head.set(0.48f, 0.25f, 1400);
    localSample.leftShoulder.set(0.45f, 0.32f, 1400);
    localSample.rightShoulder.set(0.55f, 0.32f, 1400);
    localSample.leftHand.set(0.42f, 0.55f, 1400);
    remoteSample.isPlayerPresent = true;
    remoteSample.playerCentroidX = 0.5f;
    remoteSample.leftHandElevation = 0.8f;
    remoteSample.head.set(0.5f, 0.2f, 0);
    remoteSample.leftShoulder.set(0.45f, 0.3f, 0);
    remoteSample.rightShoulder.set(0.55f, 0.3f, 0);
  }

  @Test
  public void prefersRemoteArmsWhenFresh() {
    hybrid.ingestLocalFrameForTest(localSample, null);
    assertEquals(0.2f, lastDelivered.leftHandElevation, 0.001f);

    relay.setConnected(true);
    remoteSample.leftHand.set(0.35f, 0.28f, 0);
    remoteSample.leftElbow.set(0.40f, 0.42f, 0);
    relay.deliverRemote(1L, remoteSample, System.currentTimeMillis());
    hybrid.ingestLocalFrameForTest(localSample, null);
    assertEquals(0.8f, lastDelivered.leftHandElevation, 0.001f);
    assertEquals(0.48f, lastDelivered.head.x, 0.001f);
    assertEquals(0.35f, lastDelivered.leftHand.x, 0.001f);
    assertTrue(lastDelivered.diagnostics.isRemoteAuxActive);
  }

  @Test
  public void ignoresEmptyRemoteTrack() {
    relay.setConnected(true);
    TrackingResult emptyRemote = new TrackingResult();
    emptyRemote.reset();
    emptyRemote.leftHandElevation = 0f;
    relay.deliverRemote(1L, emptyRemote, System.currentTimeMillis());
    hybrid.ingestLocalFrameForTest(localSample, null);
    assertEquals(0.2f, lastDelivered.leftHandElevation, 0.001f);
    assertEquals(0.48f, lastDelivered.head.x, 0.001f);
    assertFalse(lastDelivered.diagnostics.isRemoteAuxActive);
  }

  @Test
  public void fallsBackToLocalWhenRemoteStale() throws Exception {
    relay.setConnected(true);
    relay.deliverRemote(1L, remoteSample, System.currentTimeMillis());
    hybrid.ingestLocalFrameForTest(localSample, null);
    assertEquals(0.8f, lastDelivered.leftHandElevation, 0.001f);

    Thread.sleep(MirrorTrackingRelay.REMOTE_RESULT_MAX_AGE_MS + 30L);
    hybrid.ingestLocalFrameForTest(localSample, null);
    assertEquals(0.2f, lastDelivered.leftHandElevation, 0.001f);
    assertEquals(0.48f, lastDelivered.head.x, 0.001f);
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

  private static short[] flatDepth(int w, int h, int mm) {
    short[] depth = new short[w * h];
    Arrays.fill(depth, (short) mm);
    return depth;
  }
}
