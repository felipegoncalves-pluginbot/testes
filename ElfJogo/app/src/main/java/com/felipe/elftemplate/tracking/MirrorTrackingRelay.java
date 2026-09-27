package com.felipe.elftemplate.tracking;

/** Porta de rede para offload de tracking (host Sanbot ↔ auxiliar WiFi). */
public interface MirrorTrackingRelay {

  int DEFAULT_PORT = 8765;
  long REMOTE_RESULT_MAX_AGE_MS = 300L;
  long AUXILIARY_TIMEOUT_MS = 300L;

  interface RemoteResultListener {
    void onRemoteTrack(long seq, TrackingResult result, long remoteTimestampMs);
  }

  void start(int port);

  void stop();

  boolean isAuxiliaryConnected();

  long getLastRemoteAgeMs();

  void setRemoteResultListener(RemoteResultListener listener);

  void offerRgbFrame(byte[] nv21, int width, int height);

  void offerDepthFrame(short[] depthMm, int width, int height);
}
