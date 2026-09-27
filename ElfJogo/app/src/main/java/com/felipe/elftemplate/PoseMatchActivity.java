package com.felipe.elftemplate;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import com.felipe.elftemplate.logic.PoseMatchGameEngine;
import com.felipe.elftemplate.logic.PoseMatchView;
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
 * Atividade do Jogo Pose Match (Just Dance / Simon Says estilo Kinect). O robô propõe poses
 * corporais e o jogador deve reproduzi-las no tempo limite.
 */
public class PoseMatchActivity extends BindBaseActivity {

  private PoseMatchView matchView;
  private Button btnBack, btnToggleKinectView;
  private KinectDebugOverlayView kinectOverlay;

  private PoseMatchGameEngine engine;
  private BodyTrackingSession trackingEngine;
  private RobotGameFeedback robotFeedback;

  private final Handler gameLoopHandler = new Handler(Looper.getMainLooper());
  private boolean isRunning = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    register(PoseMatchActivity.class);
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_pose_match);

    matchView = findViewById(R.id.pose_match_view);
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

    engine = new PoseMatchGameEngine();
    matchView.setEngine(engine);

    trackingEngine = BodyTrackingSession.createLocal();
  }

  @Override
  public void onMainServiceConnected() {
    SpeechManager speech = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);
    WingMotionManager wing = (WingMotionManager) getUnitManager(FuncConstant.WINGMOTION_MANAGER);
    HeadMotionManager head = (HeadMotionManager) getUnitManager(FuncConstant.HEADMOTION_MANAGER);
    HardWareManager hardware = (HardWareManager) getUnitManager(FuncConstant.HARDWARE_MANAGER);

    robotFeedback = new RobotGameFeedback(speech, wing, head, hardware);
    robotFeedback.speak("Dança do Elf iniciada! Copie as minhas poses!");

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
                matchView.updateTracking(result);
                if (kinectOverlay != null) {
                  kinectOverlay.updateTracking(result, debugSilhouette);
                }

                if (result.isPlayerPresent) {
                  boolean matched = engine.evaluatePlayerPose(result.activeGesture, 0.5f);
                  if (matched) {
                    if (robotFeedback != null) {
                      robotFeedback.cheerPoint(false, "Perfeito! Pose correta!");
                      robotFeedback.performPoseMirror(
                          result.isLeftHandRaised, result.isRightHandRaised);
                    }

                    gameLoopHandler.postDelayed(
                        () -> {
                          engine.pickNextTargetPose();
                        },
                        1000);
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

          engine.update(0.016f);
          matchView.invalidate();

          if (engine.isRoundTimedOut() && !engine.isRoundComplete()) {
            if (robotFeedback != null) {
              robotFeedback.reactBombOrMiss("Tempo esgotado! Tente a próxima!");
            }
            engine.pickNextTargetPose();
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
