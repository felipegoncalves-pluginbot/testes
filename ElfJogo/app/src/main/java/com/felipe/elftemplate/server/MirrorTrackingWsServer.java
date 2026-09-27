package com.felipe.elftemplate.server;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;
import java.io.IOException;

/** Servidor WebSocket `/mirror` para auxiliares de tracking. */
final class MirrorTrackingWsServer extends NanoWSD {

  private static final String TAG = "MirrorWsServer";
  private final MirrorTrackingRelayWs relay;
  private volatile MirrorWsSocket auxSocket;

  MirrorTrackingWsServer(int port, MirrorTrackingRelayWs relay) {
    super(port);
    this.relay = relay;
  }

  @Override
  protected WebSocket openWebSocket(NanoHTTPD.IHTTPSession handshake) {
    String uri = handshake.getUri();
    if (uri == null || !uri.startsWith("/mirror")) {
      return null;
    }
    return new MirrorWsSocket(handshake, this);
  }

  void registerAux(MirrorWsSocket socket) {
    MirrorWsSocket previous = auxSocket;
    auxSocket = socket;
    if (previous != null && previous != socket) {
      previous.closeQuietly();
    }
    relay.onAuxConnected();
    socket.sendText(relay.helloAck());
  }

  void unregisterAux(MirrorWsSocket socket) {
    if (auxSocket == socket) {
      auxSocket = null;
      relay.onAuxDisconnected();
    }
  }

  void sendToAux(String payload) {
    MirrorWsSocket socket = auxSocket;
    if (socket != null && payload != null) {
      socket.sendText(payload);
    }
  }

  void dispatchMessage(String payload) {
    relay.onAuxMessage(payload);
  }

  static final class MirrorWsSocket extends WebSocket {

    private final MirrorTrackingWsServer owner;

    MirrorWsSocket(NanoHTTPD.IHTTPSession handshake, MirrorTrackingWsServer owner) {
      super(handshake);
      this.owner = owner;
    }

    @Override
    protected void onOpen() {
      owner.registerAux(this);
    }

    @Override
    protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
      owner.unregisterAux(this);
    }

    @Override
    protected void onMessage(WebSocketFrame message) {
      String payload = message.getTextPayload();
      if (payload == null) {
        return;
      }
      if (com.felipe.elftemplate.tracking.MirrorProtocolCodec.isHelloFromAux(payload)) {
        sendText(owner.relay.helloAck());
        return;
      }
      owner.dispatchMessage(payload);
    }

    @Override
    protected void onPong(WebSocketFrame pong) {}

    @Override
    protected void onException(IOException exception) {
      Log.w(TAG, "ws: " + exception.getMessage());
      closeQuietly();
    }

    void sendText(String payload) {
      try {
        send(payload);
      } catch (IOException e) {
        Log.w(TAG, "send: " + e.getMessage());
      }
    }

    void closeQuietly() {
      try {
        close(WebSocketFrame.CloseCode.NormalClosure, "bye", false);
      } catch (IOException ignored) {
      }
    }
  }
}
