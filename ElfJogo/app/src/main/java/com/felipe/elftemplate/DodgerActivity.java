package com.felipe.elftemplate;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import com.felipe.elftemplate.logic.DodgerGameEngine;
import com.felipe.elftemplate.logic.DodgerView;
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
 * Atividade do Jogo Dodger Runner (Corrida com Esquiva estilo Kinect Adventures). O jogador desvia
 * de obstáculos movendo o corpo, pulando ou agachando.
 */
public class DodgerActivity extends BindBaseActivity {

  private DodgerView dodgerView;
  private Button btnBack, btnToggleKinectView;
  private KinectDebugOverlayView kinectOverlay;

  private DodgerGameEngine engine;
  private BodyTrackingSession trackingEngine;
  private RobotGameFeedback robotFeedback;

  private final Handler gameLoopHandler = new Handler(Looper.getMainLooper());
  private boolean isRunning = false;
  private float lastPlayerX = 0.5f;
  private boolean lastJumping = false;
  private boolean lastDucking = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    register(DodgerActivity.class);
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_dodger);

    dodgerView = findViewById(R.id.dodger_view);
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

    engine = new DodgerGameEngine();
    dodgerView.setEngine(engine);

    trackingEngine = BodyTrackingSession.createLocal();
  }

  @Override
  public void onMainServiceConnected() {
    SpeechManager speech = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);
    WingMotionManager wing = (WingMotionManager) getUnitManager(FuncConstant.WINGMOTION_MANAGER);
    HeadMotionManager head = (HeadMotionManager) getUnitManager(FuncConstant.HEADMOTION_MANAGER);
    HardWareManager hardware = (HardWareManager) getUnitManager(FuncConstant.HARDWARE_MANAGER);

    robotFeedback = new RobotGameFeedback(speech, wing, head, hardware);
    robotFeedback.speak("Corrida de Obstáculos iniciada! Incline o corpo para desviar e pule!");

    startTrackingAndGame();
  }

  private void startTrackingAndGame() {
    HDCameraManager hdCamera = (HDCameraManager) getUnitManager(FuncConstant.HDCAMERA_MANAGER);
    trackingEngine.attachHdCamera(hdCamera);

    trackingEngine.startOnMainThread(
        this,
        (result, debugSilhouette) -> {
          runOnUiThread(
              () -> {
                if (!isRunning) {
                  return;
                }
                dodgerView.updateTracking(result);
                if (kinectOverlay != null) {
                  kinectOverlay.updateTracking(result, debugSilhouette);
                }

                if (result.isPlayerPresent) {
                  lastPlayerX = result.playerCentroidX;
                  lastJumping = result.isJumping;
                  lastDucking = result.isDucking;

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

          engine.update(1.0f);
          boolean hit = engine.checkCollision(lastPlayerX, lastJumping, lastDucking);
          if (hit) {
            if (robotFeedback != null) {
              robotFeedback.reactBombOrMiss("Cuidado com o obstáculo!");
            }
          }

          dodgerView.invalidate();
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
