package com.felipe.elfmirror;

import android.os.Handler;
import android.os.Looper;
import com.felipe.elfmirror.net.MirrorWsClient;
import com.felipe.elfmirror.protocol.MirrorProtocolCodec;
import com.felipe.elfmirror.tracking.MirrorTrackBuilder;

/** Orquestra WebSocket + inferência ML Kit para o modo espelho. */
public final class MirrorAuxSession implements MirrorWsClient.Listener {

  public interface UiCallback {
    void onStatus(String status);

    void onMetrics(long frames, long tracks, long lastSeq, String lastError);
  }

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final MirrorTrackBuilder trackBuilder = new MirrorTrackBuilder();
  private final String deviceName;

  private MirrorWsClient wsClient;
  private UiCallback uiCallback;
  private long frameCount;
  private long trackCount;
  private long lastSeq;
  private String lastError = "";

  public MirrorAuxSession(String deviceName) {
    this.deviceName = deviceName;
  }

  public void setUiCallback(UiCallback callback) {
    uiCallback = callback;
  }

  public boolean isConnected() {
    return wsClient != null && wsClient.isConnected();
  }

  public void connect(String hostIp) {
    disconnect();
    postStatus("Conectando…");
    wsClient = new MirrorWsClient(deviceName, this);
    wsClient.connect(hostIp);
  }

  public void disconnect() {
    if (wsClient != null) {
      wsClient.disconnect("user");
      wsClient = null;
    }
    postStatus("Desconectado");
    postMetrics();
  }

  public void release() {
    disconnect();
    trackBuilder.close();
  }

  @Override
  public void onConnected() {
    frameCount = 0L;
    trackCount = 0L;
    postStatus("Conectado — aguardando frames");
    postMetrics();
  }

  @Override
  public void onDisconnected(String reason) {
    postStatus("Desconectado: " + reason);
    postMetrics();
  }

  @Override
  public void onFrame(MirrorProtocolCodec.FramePayload frame) {
    frameCount++;
    lastSeq = frame.seq;
    postStatus("Processando pose");
    trackBuilder.processFrame(
        frame,
        new MirrorTrackBuilder.TrackCallback() {
          @Override
          public void onTrackReady(String trackJson) {
            if (trackJson == null || trackJson.isEmpty()) {
              return;
            }
            trackCount++;
            MirrorWsClient client = wsClient;
            if (client != null) {
              client.sendTrack(trackJson);
            }
            postMetrics();
          }

          @Override
          public void onInferError(String message) {
            lastError = message != null ? message : "erro";
            postMetrics();
          }
        });
    postMetrics();
  }

  @Override
  public void onProtocolError(String message) {
    lastError = message != null ? message : "protocolo";
    postMetrics();
  }

  private void postStatus(final String status) {
    final UiCallback callback = uiCallback;
    if (callback == null) {
      return;
    }
    mainHandler.post(() -> callback.onStatus(status));
  }

  private void postMetrics() {
    final UiCallback callback = uiCallback;
    if (callback == null) {
      return;
    }
    final long frames = frameCount;
    final long tracks = trackCount;
    final long seq = lastSeq;
    final String err = lastError;
    mainHandler.post(() -> callback.onMetrics(frames, tracks, seq, err));
  }
}
