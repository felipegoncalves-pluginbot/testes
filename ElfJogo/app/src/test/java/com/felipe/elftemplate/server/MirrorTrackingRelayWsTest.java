package com.felipe.elftemplate.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.felipe.elftemplate.tracking.KinectTrackingEngine;
import com.felipe.elftemplate.tracking.MirrorProtocolCodec;
import com.felipe.elftemplate.tracking.TrackingResult;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class MirrorTrackingRelayWsTest {

  @Test
  public void trackMessageNotifiesListener() throws Exception {
    MirrorTrackingRelayWs relay = new MirrorTrackingRelayWs();
    AtomicInteger notifications = new AtomicInteger();
    relay.setRemoteResultListener(
        (seq, result, ts) -> {
          notifications.incrementAndGet();
          assertEquals(0.45f, result.leftHandElevation, 0.001f);
        });

    TrackingResult sample = new TrackingResult();
    sample.reset();
    sample.isPlayerPresent = true;
    sample.leftHandElevation = 0.45f;
    sample.activeGesture = KinectTrackingEngine.GestureType.IDLE;
    String payload = MirrorProtocolCodec.encodeTrack(7L, System.currentTimeMillis(), sample);

    relay.onAuxMessage(payload);

    assertEquals(1, notifications.get());
    assertTrue(relay.getLastRemoteAgeMs() < 50L);
  }
}
