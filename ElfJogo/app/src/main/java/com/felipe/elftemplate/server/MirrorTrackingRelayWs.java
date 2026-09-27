package com.felipe.elftemplate.server;

import android.util.Log;
import com.felipe.elftemplate.tracking.MirrorProtocolCodec;
import com.felipe.elftemplate.tracking.MirrorTrackingRelay;
import com.felipe.elftemplate.tracking.TrackingResult;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;
import java.io.IOException;
import org.json.JSONException;

/** Implementação WebSocket do {@link MirrorTrackingRelay} (host Sanbot). */
public final class MirrorTrackingRelayWs implements MirrorTrackingRelay {

  private static final String TAG = "MirrorRelayWs";

  private MirrorTrackingWsServer server;
  private RemoteResultListener remoteListener;
  private final TrackingResult decodeScratch = new TrackingResult();
  private volatile long lastRemoteAtMs;
  private volatile boolean auxConnected;
  private long frameSeq;
  private short[] pendingDepth;
  private int pendingDepthW;
  private int pendingDepthH;

  @Override
  public void start(int port) {
    stop();
    server = new MirrorTrackingWsServer(port, this);
    try {
      server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    } catch (IOException e) {
      Log.e(TAG, "Falha ao iniciar relay espelho: " + e.getMessage(), e);
      server = null;
    }
  }

  @Override
  public void stop() {
    auxConnected = false;
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  @Override
  public boolean isAuxiliaryConnected() {
    return auxConnected && server != null;
  }

  @Override
  public long getLastRemoteAgeMs() {
    if (lastRemoteAtMs <= 0) {
      return Long.MAX_VALUE;
    }
    return System.currentTimeMillis() - lastRemoteAtMs;
  }

  @Override
  public void setRemoteResultListener(RemoteResultListener listener) {
    remoteListener = listener;
  }

  @Override
  public void offerRgbFrame(byte[] nv21, int width, int height) {
    MirrorTrackingWsServer active = server;
    if (active == null || !auxConnected || nv21 == null) {
      return;
    }
    try {
      long seq = ++frameSeq;
      long now = System.currentTimeMillis();
      String payload =
          MirrorProtocolCodec.encodeFrame(
              seq, now, nv21, width, height, pendingDepth, pendingDepthW, pendingDepthH);
      active.sendToAux(payload);
      active.sendToAux(MirrorProtocolCodec.encodePing(seq, now));
    } catch (JSONException e) {
      Log.w(TAG, "encode frame: " + e.getMessage());
    }
  }

  @Override
  public void offerDepthFrame(short[] depthMm, int width, int height) {
    pendingDepth = depthMm;
    pendingDepthW = width;
    pendingDepthH = height;
  }

  void onAuxConnected() {
    auxConnected = true;
    lastRemoteAtMs = System.currentTimeMillis();
  }

  void onAuxDisconnected() {
    auxConnected = false;
  }

  void onAuxMessage(String payload) {
    String type = MirrorProtocolCodec.readType(payload);
    if ("pong".equals(type)) {
      lastRemoteAtMs = System.currentTimeMillis();
      return;
    }
    if ("track".equals(type)) {
      try {
        MirrorProtocolCodec.applyTrackPayload(payload, decodeScratch);
        lastRemoteAtMs = System.currentTimeMillis();
        RemoteResultListener listener = remoteListener;
        if (listener != null) {
          listener.onRemoteTrack(
              MirrorProtocolCodec.readSeq(payload),
              decodeScratch,
              MirrorProtocolCodec.readTimestamp(payload));
        }
      } catch (JSONException e) {
        Log.w(TAG, "track inválido: " + e.getMessage());
      }
    }
  }

  String helloAck() {
    try {
      return MirrorProtocolCodec.encodeHelloAck();
    } catch (JSONException e) {
      return "{\"v\":1,\"type\":\"hello_ack\"}";
    }
  }
}
