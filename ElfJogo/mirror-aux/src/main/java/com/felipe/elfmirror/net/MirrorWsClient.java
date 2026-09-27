package com.felipe.elfmirror.net;

import android.util.Log;
import androidx.annotation.Nullable;
import com.felipe.elfmirror.protocol.MirrorProtocolCodec;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.json.JSONException;

/** Cliente WebSocket auxiliar — conecta ao host Sanbot em /mirror. */
public final class MirrorWsClient {

  private static final String TAG = "MirrorWsClient";
  private static final int DEFAULT_PORT = 8765;

  public interface Listener {
    void onConnected();

    void onDisconnected(String reason);

    void onFrame(MirrorProtocolCodec.FramePayload frame);

    void onProtocolError(String message);
  }

  private final OkHttpClient httpClient =
      new OkHttpClient.Builder()
          .readTimeout(0, TimeUnit.MILLISECONDS)
          .pingInterval(15, TimeUnit.SECONDS)
          .build();

  private final String deviceName;
  private final Listener listener;
  private final MirrorProtocolCodec.FramePayload frameScratch =
      new MirrorProtocolCodec.FramePayload();

  private WebSocket webSocket;
  private volatile boolean connected;

  public MirrorWsClient(String deviceName, Listener listener) {
    this.deviceName = deviceName;
    this.listener = listener;
  }

  public boolean isConnected() {
    return connected;
  }

  public void connect(String hostIp) {
    disconnect("reconnect");
    String url = "ws://" + hostIp.trim() + ":" + DEFAULT_PORT + "/mirror";
    Request request = new Request.Builder().url(url).build();
    webSocket = httpClient.newWebSocket(request, new SocketCallbacks());
  }

  public void disconnect(String reason) {
    connected = false;
    WebSocket active = webSocket;
    webSocket = null;
    if (active != null) {
      active.close(1000, reason != null ? reason : "bye");
    }
  }

  public void sendTrack(String trackJson) {
    WebSocket active = webSocket;
    if (active != null && trackJson != null) {
      active.send(trackJson);
    }
  }

  private void sendPong(long seq) {
    try {
      sendTrack(MirrorProtocolCodec.encodePong(seq));
    } catch (JSONException e) {
      Log.w(TAG, "pong: " + e.getMessage());
    }
  }

  private final class SocketCallbacks extends WebSocketListener {

    @Override
    public void onOpen(WebSocket socket, Response response) {
      connected = true;
      try {
        socket.send(MirrorProtocolCodec.encodeHello(deviceName));
      } catch (JSONException e) {
        listener.onProtocolError(e.getMessage());
      }
      listener.onConnected();
    }

    @Override
    public void onMessage(WebSocket socket, String text) {
      String type = MirrorProtocolCodec.readType(text);
      if ("hello_ack".equals(type)) {
        return;
      }
      if ("ping".equals(type)) {
        sendPong(MirrorProtocolCodec.readSeq(text));
        return;
      }
      if ("frame".equals(type)) {
        try {
          if (MirrorProtocolCodec.parseFrame(text, frameScratch)) {
            listener.onFrame(copyFrame(frameScratch));
          }
        } catch (JSONException e) {
          listener.onProtocolError(e.getMessage());
        }
        return;
      }
      Log.d(TAG, "ignored type=" + type);
    }

    @Override
    public void onMessage(WebSocket socket, ByteString bytes) {
      Log.w(TAG, "binary frame ignored");
    }

    @Override
    public void onClosing(WebSocket socket, int code, String reason) {
      socket.close(code, reason);
    }

    @Override
    public void onClosed(WebSocket socket, int code, String reason) {
      connected = false;
      listener.onDisconnected(reason != null ? reason : "closed");
    }

    @Override
    public void onFailure(WebSocket socket, Throwable t, @Nullable Response response) {
      connected = false;
      String reason = t != null ? t.getMessage() : "failure";
      listener.onDisconnected(reason);
    }
  }

  private static MirrorProtocolCodec.FramePayload copyFrame(MirrorProtocolCodec.FramePayload src) {
    MirrorProtocolCodec.FramePayload copy = new MirrorProtocolCodec.FramePayload();
    copy.seq = src.seq;
    copy.timestampMs = src.timestampMs;
    copy.rgbWidth = src.rgbWidth;
    copy.rgbHeight = src.rgbHeight;
    copy.depthWidth = src.depthWidth;
    copy.depthHeight = src.depthHeight;
    copy.rgbNv21 = src.rgbNv21;
    copy.depthMm = src.depthMm;
    return copy;
  }
}
