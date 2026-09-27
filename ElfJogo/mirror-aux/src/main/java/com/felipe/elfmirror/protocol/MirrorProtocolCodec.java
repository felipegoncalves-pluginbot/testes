package com.felipe.elfmirror.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import org.json.JSONException;
import org.json.JSONObject;

/** Mensagens JSON v1 do protocolo espelho (frame, track, ping). */
public final class MirrorProtocolCodec {

  public static final int VERSION = 1;

  private MirrorProtocolCodec() {}

  public static String encodeHello(String deviceName) throws JSONException {
    JSONObject json = new JSONObject();
    json.put("v", VERSION);
    json.put("type", "hello");
    json.put("role", "aux");
    json.put("name", deviceName);
    return json.toString();
  }

  public static String encodePong(long seq) throws JSONException {
    JSONObject json = new JSONObject();
    json.put("v", VERSION);
    json.put("type", "pong");
    json.put("seq", seq);
    return json.toString();
  }

  public static String encodeTrack(long seq, long timestampMs, TrackingResult result)
      throws JSONException {
    JSONObject json = new JSONObject();
    json.put("v", VERSION);
    json.put("type", "track");
    json.put("seq", seq);
    json.put("t", timestampMs);
    json.put("result", TrackingResultCodec.toJson(result));
    return json.toString();
  }

  public static String readType(String payload) {
    if (payload == null || payload.isEmpty()) {
      return "";
    }
    try {
      return new JSONObject(payload).optString("type", "");
    } catch (JSONException ignored) {
      return "";
    }
  }

  public static long readSeq(String payload) {
    try {
      return new JSONObject(payload).optLong("seq", -1L);
    } catch (JSONException ignored) {
      return -1L;
    }
  }

  public static boolean parseFrame(String payload, FramePayload out) throws JSONException {
    JSONObject json = new JSONObject(payload);
    if (!"frame".equals(json.optString("type"))) {
      return false;
    }
    out.seq = json.optLong("seq", 0L);
    out.timestampMs = json.optLong("t", System.currentTimeMillis());
    out.rgbWidth = json.optInt("rgbW", 0);
    out.rgbHeight = json.optInt("rgbH", 0);
    out.depthWidth = json.optInt("depthW", 0);
    out.depthHeight = json.optInt("depthH", 0);
    out.rgbNv21 = decodeBase64(json.optString("rgbB64", ""));
    out.depthMm = decodeDepth(json.optString("depthB64", ""), out.depthWidth * out.depthHeight);
    return out.rgbNv21 != null && out.rgbWidth > 0 && out.rgbHeight > 0;
  }

  private static byte[] decodeBase64(String encoded) {
    if (encoded == null || encoded.isEmpty()) {
      return null;
    }
    return Base64.getDecoder().decode(encoded);
  }

  private static short[] decodeDepth(String encoded, int expectedSamples) {
    if (encoded == null || encoded.isEmpty() || expectedSamples <= 0) {
      return null;
    }
    byte[] bytes = decodeBase64(encoded);
    if (bytes == null) {
      return null;
    }
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    int samples = bytes.length / 2;
    if (samples < expectedSamples) {
      expectedSamples = samples;
    }
    short[] depth = new short[expectedSamples];
    for (int i = 0; i < expectedSamples; i++) {
      depth[i] = buffer.getShort();
    }
    return depth;
  }

  /** Frame decodificado do host Sanbot. */
  public static final class FramePayload {
    public long seq;
    public long timestampMs;
    public byte[] rgbNv21;
    public int rgbWidth;
    public int rgbHeight;
    public short[] depthMm;
    public int depthWidth;
    public int depthHeight;
  }
}
