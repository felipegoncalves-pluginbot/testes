package com.felipe.elftemplate;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import com.felipe.elftemplate.logic.TennisGameEngine;
import com.felipe.elftemplate.movement.RobotGameFeedback;
import com.felipe.elftemplate.tracking.KinectDebugOverlayView;
import com.felipe.elftemplate.tracking.KinectTrackingEngine;
import com.felipe.elftemplate.tracking3d.BodyTrackingSession;
import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.HeadMotionManager;
import com.sanbot.opensdk.function.unit.SpeechManager;
import com.sanbot.opensdk.function.unit.WingMotionManager;

/**
 * Atividade do Jogo de Tênis estilo Kinect Sports. Rastreia as raquetadas do jogador no ar via
 * Orbbec Astra 3D.
 */
public class TennisActivity extends BindBaseActivity {
  private static final String TAG = "TennisActivity";

  private TennisGameEngine gameEngine;
  private BodyTrackingSession trackingEngine;
  private RobotGameFeedback robotFeedback;

  private TextView tvScore, tvStatus;
  private View ballView, gameContainer;
  private Button btnBack, btnToggleKinectView;
  private KinectDebugOverlayView kinectOverlay;

  private final Handler gameHandler = new Handler(Looper.getMainLooper());
  private boolean isRunning = false;
  private float lastPlayerX = 0.5f;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    register(TennisActivity.class);
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_tennis);
    initUI();
    initLogic();
  }

  private void initUI() {
    tvScore = (TextView) findViewById(R.id.tv_score);
    tvStatus = (TextView) findViewById(R.id.tv_status);
    ballView = findViewById(R.id.ball_view);
    gameContainer = findViewById(R.id.game_container);
    btnBack = findViewById(R.id.btn_back);
    btnToggleKinectView = findViewById(R.id.btn_toggle_kinect_view);
    kinectOverlay = findViewById(R.id.kinect_debug_overlay);

    ballView.setVisibility(View.VISIBLE);

    btnBack.setOnClickListener(v -> finish());
    btnToggleKinectView.setOnClickListener(
        v -> {
          if (kinectOverlay != null) {
            kinectOverlay.toggleVisibility();
          }
        });
  }

  private void initLogic() {
    gameEngine = new TennisGameEngine();
    trackingEngine = BodyTrackingSession.createLocal();
  }

  @Override
  public void onMainServiceConnected() {
    SpeechManager speech = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);
    WingMotionManager wing = (WingMotionManager) getUnitManager(FuncConstant.WINGMOTION_MANAGER);
    HeadMotionManager head = (HeadMotionManager) getUnitManager(FuncConstant.HEADMOTION_MANAGER);
    HardWareManager hardware = (HardWareManager) getUnitManager(FuncConstant.HARDWARE_MANAGER);

    robotFeedback = new RobotGameFeedback(speech, wing, head, hardware);
    robotFeedback.speak("Jogo de tênis iniciado! Prepare a sua raquete!");

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
                if (kinectOverlay != null) {
                  kinectOverlay.updateTracking(result, debugSilhouette);
                }

                if (result.isPlayerPresent) {
                  lastPlayerX = result.playerCentroidX;
                  if (robotFeedback != null) {
                    robotFeedback.trackTargetX(result.playerCentroidX);
                  }

                  // Se detectou movimento de raquetada ou mão alta
                  if (result.activeGesture == KinectTrackingEngine.GestureType.SWING_UP
                      || result.isRightHandRaised
                      || result.isLeftHandRaised) {
                    gameEngine.onPlayerHit(lastPlayerX);
                    tvStatus.setText("REBATEU!");
                    gameHandler.postDelayed(() -> tvStatus.setText("Jogando..."), 800);
                  }
                }
              });
        });

    gameEngine.start();
    isRunning = true;
    gameHandler.post(gameLoopRunnable);
  }

  private final Runnable gameLoopRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (!isRunning) return;

          if (gameEngine.update()) {
            updateUIPlacar();
            if (robotFeedback != null) {
              robotFeedback.cheerPoint(true, "Ponto meu! Vamos continuar!");
            }
          }

          updateBallPosition();

          if (gameEngine.getBallY() < 0.15f) {
            gameEngine.onRobotHit();
            if (robotFeedback != null) {
              robotFeedback.performPoseMirror(true, false);
            }
          }

          gameHandler.postDelayed(this, 30);
        }
      };

  private void updateUIPlacar() {
    tvScore.setText(
        String.format("%02d - %02d", gameEngine.getRobotScore(), gameEngine.getPlayerScore()));
  }

  private void updateBallPosition() {
    int containerW = gameContainer.getWidth();
    int containerH = gameContainer.getHeight();
    if (containerW == 0 || containerH == 0) return;

    float bx = gameEngine.getBallX() * containerW;
    float by = gameEngine.getBallY() * containerH;

    ballView.setX(bx - ballView.getWidth() / 2f);
    ballView.setY(by - ballView.getHeight() / 2f);

    float scale = 0.5f + (gameEngine.getBallY() * 1.5f);
    ballView.setScaleX(scale);
    ballView.setScaleY(scale);
  }

  @Override
  protected void onStop() {
    isRunning = false;
    gameHandler.removeCallbacks(gameLoopRunnable);
    if (trackingEngine != null) {
      trackingEngine.stop();
    }
    if (robotFeedback != null) {
      robotFeedback.stopAll();
    }
    super.onStop();
  }
}
