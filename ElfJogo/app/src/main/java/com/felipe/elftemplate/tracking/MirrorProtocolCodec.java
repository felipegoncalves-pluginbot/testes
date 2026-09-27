package com.felipe.elftemplate.tracking;

import android.util.Base64;
import org.json.JSONException;
import org.json.JSONObject;

/** Mensagens JSON v1 do protocolo espelho (frame, track, ping). */
public final class MirrorProtocolCodec {

  public static final int VERSION = 1;

  private MirrorProtocolCodec() {}

  public static String encodeHelloAck() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("v", VERSION);
    json.put("type", "hello_ack");
    json.put("role", "host");
    json.put("proto", VERSION);
    return json.toString();
  }

  public static String encodePing(long seq, long timestampMs) throws JSONException {
    JSONObject json = new JSONObject();
    json.put("v", VERSION);
    json.put("type", "ping");
    json.put("seq", seq);
    json.put("t", timestampMs);
    return json.toString();
  }

  public static String encodePong(long seq) throws JSONException {
    JSONObject json = new JSONObject();
    json.put("v", VERSION);
    json.put("type", "pong");
    json.put("seq", seq);
    return json.toString();
  }

  public static String encodeFrame(
      long seq,
      long timestampMs,
      byte[] nv21,
      int rgbWidth,
      int rgbHeight,
      short[] depthMm,
      int depthWidth,
      int depthHeight)
      throws JSONException {
    JSONObject json = new JSONObject();
    json.put("v", VERSION);
    json.put("type", "frame");
    json.put("seq", seq);
    json.put("t", timestampMs);
    json.put("rgbW", rgbWidth);
    json.put("rgbH", rgbHeight);
    json.put("rgbB64", Base64.encodeToString(nv21, 0, nv21.length, Base64.NO_WRAP));
    if (depthMm == null || depthWidth <= 0 || depthHeight <= 0) {
      json.put("depthW", 0);
      json.put("depthH", 0);
      json.put("depthB64", "");
    } else {
      json.put("depthW", depthWidth);
      json.put("depthH", depthHeight);
      json.put("depthB64", encodeDepthBase64(depthMm, depthWidth * depthHeight));
    }
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
      JSONObject json = new JSONObject(payload);
      return json.optString("type", "");
    } catch (JSONException ignored) {
      return "";
    }
  }

  public static boolean isHelloFromAux(String payload) {
    try {
      JSONObject json = new JSONObject(payload);
      return VERSION == json.optInt("v", 0)
          && "hello".equals(json.optString("type"))
          && "aux".equals(json.optString("role"));
    } catch (JSONException ignored) {
      return false;
    }
  }

  public static long readSeq(String payload) {
    try {
      return new JSONObject(payload).optLong("seq", -1L);
    } catch (JSONException ignored) {
      return -1L;
    }
  }

  public static void applyTrackPayload(String payload, TrackingResult out) throws JSONException {
    JSONObject json = new JSONObject(payload);
    JSONObject result = json.getJSONObject("result");
    TrackingResultCodec.applyJson(result, out);
  }

  public static long readTimestamp(String payload) {
    try {
      return new JSONObject(payload).optLong("t", 0L);
    } catch (JSONException ignored) {
      return 0L;
    }
  }

  private static String encodeDepthBase64(short[] depthMm, int length) {
    byte[] bytes = new byte[length * 2];
    int bi = 0;
    for (int i = 0; i < length; i++) {
      int v = depthMm[i] & 0xFFFF;
      bytes[bi++] = (byte) (v & 0xFF);
      bytes[bi++] = (byte) ((v >> 8) & 0xFF);
    }
    return Base64.encodeToString(bytes, 0, bytes.length, Base64.NO_WRAP);
  }
}
