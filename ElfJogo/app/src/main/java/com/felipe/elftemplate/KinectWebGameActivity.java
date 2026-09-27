package com.felipe.elftemplate;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import com.felipe.elftemplate.movement.RobotGameFeedback;
import com.felipe.elftemplate.tracking.KinectDebugOverlayView;
import com.felipe.elftemplate.tracking.KinectWebBridge;
import com.felipe.elftemplate.tracking3d.BodyTrackingSession;
import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.HeadMotionManager;
import com.sanbot.opensdk.function.unit.SpeechManager;
import com.sanbot.opensdk.function.unit.WingMotionManager;

/**
 * Atividade de Alta Performance para Jogos de Código Aberto HTML5/Canvas/WebGL estilo Kinect.
 * Injeta os dados da câmera Orbbec Astra diretamente no motor de jogo via JavascriptInterface.
 */
public class KinectWebGameActivity extends BindBaseActivity {
  private static final String TAG = "KinectWebGameActivity";

  public static final String EXTRA_GAME_URL = "game_url";
  public static final String EXTRA_GAME_TITLE = "game_title";

  private WebView webView;
  private Button btnBack, btnToggleKinectView;
  private KinectDebugOverlayView kinectOverlay;

  private BodyTrackingSession trackingEngine;
  private RobotGameFeedback robotFeedback;
  private KinectWebBridge webBridge;

  private String gameUrl = "file:///android_asset/games/fruit_ninja/index.html";
  private String gameTitle = "Jogo";
  private boolean isPageLoaded = false;
  private boolean isDestroyed = false;

  @Override
  @SuppressLint("SetJavaScriptEnabled")
  protected void onCreate(Bundle savedInstanceState) {
    register(KinectWebGameActivity.class);
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_kinect_web_game);

    Intent intent = getIntent();
    if (intent != null) {
      if (intent.hasExtra(EXTRA_GAME_URL)) gameUrl = intent.getStringExtra(EXTRA_GAME_URL);
      if (intent.hasExtra(EXTRA_GAME_TITLE)) gameTitle = intent.getStringExtra(EXTRA_GAME_TITLE);
    }

    webView = findViewById(R.id.game_web_view);
    btnBack = findViewById(R.id.btn_back);
    btnToggleKinectView = findViewById(R.id.btn_toggle_kinect_view);
    kinectOverlay = findViewById(R.id.kinect_debug_overlay);

    btnBack.setOnClickListener(v -> finish());
    btnToggleKinectView.setOnClickListener(
        v -> {
          if (kinectOverlay != null) {
            kinectOverlay.toggleVisibility();
          }
        });

    setupWebView();
    trackingEngine = BodyTrackingSession.createLocal();
  }

  @SuppressLint("SetJavaScriptEnabled")
  private void setupWebView() {
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setDatabaseEnabled(true);
    settings.setAllowFileAccess(true);
    settings.setAllowContentAccess(true);
    settings.setAllowFileAccessFromFileURLs(true);
    settings.setAllowUniversalAccessFromFileURLs(true);

    // Aceleração de hardware
    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

    webBridge =
        new KinectWebBridge(
            new KinectWebBridge.BridgeListener() {
              @Override
              public void onScoreEvent(int points, int combo) {
                if (robotFeedback != null) {
                  if (combo >= 3) {
                    robotFeedback.cheerPoint(false, "Combo incrível x" + combo + "!");
                  }
                }
              }

              @Override
              public void onBombEvent() {
                if (robotFeedback != null) {
                  robotFeedback.reactBombOrMiss("Cuidado com a bomba!");
                }
              }

              @Override
              public void onGameOverEvent(int finalScore) {
                if (robotFeedback != null) {
                  robotFeedback.speak("Fim de jogo! Você fez " + finalScore + " pontos!");
                }
              }

              @Override
              public void onTrackHeadEvent(float x) {
                if (robotFeedback != null) {
                  robotFeedback.trackTargetX(x);
                }
              }
            });

    webView.addJavascriptInterface(webBridge, "KinectBridge");

    webView.setWebViewClient(
        new WebViewClient() {
          @Override
          public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            isPageLoaded = true;
            Log.i(TAG, "[WEB-PAGE] Jogo carregado com sucesso: " + url);
          }
        });

    webView.setWebChromeClient(
        new WebChromeClient() {
          @Override
          public boolean onConsoleMessage(ConsoleMessage cm) {
            Log.d(
                "WebConsole",
                String.format("[%s:%d] %s", cm.sourceId(), cm.lineNumber(), cm.message()));
            return true;
          }
        });

    webView.loadUrl(gameUrl);
  }

  @Override
  public void onMainServiceConnected() {
    SpeechManager speech = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);
    WingMotionManager wing = (WingMotionManager) getUnitManager(FuncConstant.WINGMOTION_MANAGER);
    HeadMotionManager head = (HeadMotionManager) getUnitManager(FuncConstant.HEADMOTION_MANAGER);
    HardWareManager hardware = (HardWareManager) getUnitManager(FuncConstant.HARDWARE_MANAGER);

    robotFeedback = new RobotGameFeedback(speech, wing, head, hardware);
    robotFeedback.speak(gameTitle + " iniciado! Mova o corpo para jogar!");

    startTracking();
  }

  private void startTracking() {
    HDCameraManager hdCamera = (HDCameraManager) getUnitManager(FuncConstant.HDCAMERA_MANAGER);
    trackingEngine.attachHdCamera(hdCamera);

    trackingEngine.start(
        this,
        (result, debugSilhouette) -> {
          if (isDestroyed) return;

          runOnUiThread(
              () -> {
                if (isDestroyed) return;

                if (kinectOverlay != null) {
                  kinectOverlay.updateTracking(result, debugSilhouette);
                }

                if (result.isPlayerPresent && robotFeedback != null) {
                  robotFeedback.trackTargetX(result.playerCentroidX);
                }

                // Injeta dados de rastreamento no jogo HTML5 via evaluateJavascript de forma segura
                if (webView != null && webBridge != null && isPageLoaded) {
                  try {
                    String json = webBridge.formatTrackingJson(result);
                    webView.evaluateJavascript(
                        "if (window.onKinectFrame) { window.onKinectFrame(" + json + "); }", null);
                  } catch (Throwable ignored) {
                  }
                }
              });
        });
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (webView != null) {
      webView.onPause();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (webView != null) {
      webView.onResume();
    }
  }

  @Override
  protected void onStop() {
    isDestroyed = true;
    if (trackingEngine != null) {
      trackingEngine.stop();
    }
    if (robotFeedback != null) {
      robotFeedback.stopAll();
    }
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    isDestroyed = true;
    if (webView != null) {
      try {
        webView.removeJavascriptInterface("KinectBridge");
        webView.loadUrl("about:blank");
        webView.stopLoading();
        webView.setWebChromeClient(null);
        webView.setWebViewClient(null);
        webView.destroy();
      } catch (Throwable ignored) {
      }
    }
    super.onDestroy();
  }
}
