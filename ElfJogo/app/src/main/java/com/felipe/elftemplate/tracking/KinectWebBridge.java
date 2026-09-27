package com.felipe.elftemplate.tracking;

import android.util.Log;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;

/**
 * Ponte Javascript-Java (JavascriptInterface) para jogos HTML5/Canvas estilo Kinect. Permite que o
 * jogo envie eventos de pontuação, combos e bombas para disparar movimentos físicos no robô Sanbot
 * (asas, cabeça, LEDs e voz TTS).
 */
public class KinectWebBridge {
  private static final String TAG = "KinectWebBridge";

  public interface BridgeListener {
    void onScoreEvent(int points, int combo);

    void onBombEvent();

    void onGameOverEvent(int finalScore);

    void onTrackHeadEvent(float x);
  }

  private final BridgeListener listener;

  public KinectWebBridge(BridgeListener listener) {
    this.listener = listener;
  }

  @JavascriptInterface
  public void onScore(int points, int combo) {
    Log.i(TAG, "[WEB-GAME] Pontuou: " + points + " pts | Combo: x" + combo);
    if (listener != null) {
      listener.onScoreEvent(points, combo);
    }
  }

  @JavascriptInterface
  public void onBombHit() {
    Log.w(TAG, "[WEB-GAME] Bomba atingida!");
    if (listener != null) {
      listener.onBombEvent();
    }
  }

  @JavascriptInterface
  public void onGameOver(int finalScore) {
    Log.i(TAG, "[WEB-GAME] Fim de Jogo! Placar Final: " + finalScore);
    if (listener != null) {
      listener.onGameOverEvent(finalScore);
    }
  }

  @JavascriptInterface
  public void trackHead(float x) {
    if (listener != null) {
      listener.onTrackHeadEvent(x);
    }
  }

  @JavascriptInterface
  public void log(String msg) {
    Log.d(TAG, "[WEB-LOG] " + msg);
  }

  /** Formata os dados de rastreamento da Astra em JSON leve para o JavaScript. */
  public String formatTrackingJson(TrackingResult result) {
    try {
      JSONObject json = new JSONObject();
      json.put("isPlayerPresent", result.isPlayerPresent);
      json.put("centroidX", (double) result.playerCentroidX);
      json.put("centroidY", (double) result.playerCentroidY);
      json.put("distanceZ", result.playerDistanceZ);
      json.put("leftHandX", (double) result.leftHandX);
      json.put("leftHandY", (double) result.leftHandY);
      json.put("rightHandX", (double) result.rightHandX);
      json.put("rightHandY", (double) result.rightHandY);
      json.put("isLeftHandRaised", result.isLeftHandRaised);
      json.put("isRightHandRaised", result.isRightHandRaised);
      json.put("isJumping", result.isJumping);
      json.put("isDucking", result.isDucking);
      json.put("isPoseFusionActive", result.diagnostics.isPoseFusionActive);
      json.put("isRemoteAuxActive", result.diagnostics.isRemoteAuxActive);
      json.put("isSeatedPose", result.diagnostics.isSeatedPose);
      json.put("headX", (double) result.head.x);
      json.put("headY", (double) result.head.y);
      json.put("gesture", result.activeGesture != null ? result.activeGesture.name() : "IDLE");
      return json.toString();
    } catch (Exception e) {
      return "{\"isPlayerPresent\":false}";
    }
  }
}
