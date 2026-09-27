package com.felipe.elftemplate;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import com.felipe.elftemplate.logic.FruitSlicerGameEngine;
import com.felipe.elftemplate.logic.FruitSlicerView;
import com.felipe.elftemplate.movement.RobotGameFeedback;
import com.felipe.elftemplate.tracking.KinectDebugOverlayView;
import com.felipe.elftemplate.tracking3d.BodyTrackingSession;
import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.HeadMotionManager;
import com.sanbot.opensdk.function.unit.SpeechManager;
import com.sanbot.opensdk.function.unit.WingMotionManager;

/**
 * Atividade do Jogo Fruit Slicer (Ninja das Frutas estilo Kinect). Rastreia as mãos do jogador no
 * ar via Orbbec Astra ou toque na tela.
 */
public class FruitSlicerActivity extends BindBaseActivity {

  private FruitSlicerView slicerView;
  private Button btnRestart, btnBack, btnToggleKinectView;
  private KinectDebugOverlayView kinectOverlay;

  private FruitSlicerGameEngine engine;
  private BodyTrackingSession trackingEngine;
  private RobotGameFeedback robotFeedback;

  private final Handler gameLoopHandler = new Handler(Looper.getMainLooper());
  private boolean isRunning = false;
  private long lastWaveSpawnTime = 0;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    register(FruitSlicerActivity.class);
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_fruit_slicer);

    slicerView = findViewById(R.id.fruit_slicer_view);
    btnRestart = findViewById(R.id.btn_restart);
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

    engine = new FruitSlicerGameEngine();
    slicerView.setEngine(engine);

    slicerView.setSliceListener(
        (x1, y1, x2, y2) -> {
          int sliced = engine.processSlice(x1, y1, x2, y2);
          if (sliced > 0) {
            if (robotFeedback != null) {
              if (engine.getCombo() >= 3) {
                robotFeedback.cheerPoint(false, "Combo x" + engine.getCombo() + "!");
              }
            }
          }
        });

    btnRestart.setOnClickListener(
        v -> {
          engine.reset();
          btnRestart.setVisibility(View.GONE);
        });

    trackingEngine = BodyTrackingSession.createLocal();
  }

  @Override
  public void onMainServiceConnected() {
    SpeechManager speech = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);
    WingMotionManager wing = (WingMotionManager) getUnitManager(FuncConstant.WINGMOTION_MANAGER);
    HeadMotionManager head = (HeadMotionManager) getUnitManager(FuncConstant.HEADMOTION_MANAGER);
    HardWareManager hardware = (HardWareManager) getUnitManager(FuncConstant.HARDWARE_MANAGER);

    robotFeedback = new RobotGameFeedback(speech, wing, head, hardware);
    robotFeedback.speak("Ninja das Frutas iniciado! Use as mãos para cortar!");

    startTrackingAndGame();
  }

  private void startTrackingAndGame() {
    HDCameraManager hdCamera = (HDCameraManager) getUnitManager(FuncConstant.HDCAMERA_MANAGER);
    trackingEngine.attachHdCamera(hdCamera);

    trackingEngine.start(
        this,
        (result, debugSilhouette) -> {
          runOnUiThread(
              () -> {
                if (!isRunning) {
                  return;
                }
                if (kinectOverlay != null) {
                  kinectOverlay.updateTracking(result, debugSilhouette);
                }

                if (result.isPlayerPresent) {
                  float handX = result.isRightHandRaised ? result.rightHandX : result.leftHandX;
                  float handY = result.isRightHandRaised ? result.rightHandY : result.leftHandY;
                  slicerView.addBladePoint(handX, handY);

                  if (robotFeedback != null) {
                    robotFeedback.trackTargetX(result.playerCentroidX);
                  }
                }
              });
        });

    isRunning = true;
    gameLoopHandler.post(gameRunnable);
  }

  private final Runnable gameRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (!isRunning) return;

          long now = System.currentTimeMillis();
          if (now - lastWaveSpawnTime > 2400) {
            engine.spawnWave();
            lastWaveSpawnTime = now;
          }

          engine.update(1.0f);
          slicerView.invalidate();

          if (engine.isGameOver()) {
            btnRestart.setVisibility(View.VISIBLE);
          }

          gameLoopHandler.postDelayed(this, 16);
        }
      };

  @Override
  protected void onStop() {
    isRunning = false;
    gameLoopHandler.removeCallbacks(gameRunnable);
    if (trackingEngine != null) {
      trackingEngine.stop();
    }
    if (robotFeedback != null) {
      robotFeedback.stopAll();
    }
    super.onStop();
  }
}
