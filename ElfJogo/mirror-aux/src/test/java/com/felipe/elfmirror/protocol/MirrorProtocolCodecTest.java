package com.felipe.elfmirror.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

public class MirrorProtocolCodecTest {

  @Test
  public void helloContainsAuxRole() throws Exception {
    String payload = MirrorProtocolCodec.encodeHello("pixel-test");
    assertTrue(payload.contains("\"role\":\"aux\""));
    assertTrue(payload.contains("pixel-test"));
  }

  @Test
  public void parseFrameDecodesRgbDimensions() throws Exception {
    byte[] nv21 = new byte[320 * 240 * 3 / 2];
    nv21[0] = 42;
    String b64 = java.util.Base64.getEncoder().encodeToString(nv21);
    String json =
        "{\"v\":1,\"type\":\"frame\",\"seq\":9,\"t\":1000,"
            + "\"rgbW\":320,\"rgbH\":240,\"rgbB64\":\""
            + b64
            + "\",\"depthW\":0,\"depthH\":0,\"depthB64\":\"\"}";

    MirrorProtocolCodec.FramePayload out = new MirrorProtocolCodec.FramePayload();
    assertTrue(MirrorProtocolCodec.parseFrame(json, out));
    assertEquals(9L, out.seq);
    assertEquals(320, out.rgbWidth);
    assertEquals(240, out.rgbHeight);
    assertEquals(42, out.rgbNv21[0]);
  }

  @Test
  public void encodeTrackMatchesHostShape() throws Exception {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.leftHandElevation = 0.55f;
    result.isLeftHandRaised = true;
    result.activeGesture = GestureType.LEFT_HAND_UP;
    result.leftHand.set(0.22f, 0.54f, 1380);

    String payload = MirrorProtocolCodec.encodeTrack(3L, 2000L, result);
    assertTrue(payload.contains("\"type\":\"track\""));
    assertTrue(payload.contains("\"leftElev\":0.55"));
    assertTrue(payload.contains("LEFT_HAND_UP"));
  }
}
