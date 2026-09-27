package com.felipe.elftemplate;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import com.felipe.elftemplate.movement.FollowMovementController;
import com.felipe.elftemplate.tracking.AstraDepthController;
import com.felipe.elftemplate.tracking.CameraController;
import com.felipe.elftemplate.tracking.FaceOverlayView;
import com.felipe.elftemplate.tracking.HybridPersonTracker;
import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.HeadMotionManager;
import com.sanbot.opensdk.function.unit.SpeechManager;
import com.sanbot.opensdk.function.unit.WheelMotionManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Atividade Siga-me Ultra-Robusta (V4 - Híbrida). Utiliza Câmera HD com ML Kit Face Detection +
 * Câmera Astra para profundidade.
 */
public class FollowActivity extends BindBaseActivity {

  private static final String TAG = "FollowActivity";
  private static final int VISION_WIDTH = 640;
  private static final int VISION_HEIGHT = 480;

  private HDCameraManager hdCameraManager;
  private WheelMotionManager wheelManager;
  private HeadMotionManager headManager;
  private SpeechManager speechManager;

  private CameraController cameraController;
  private AstraDepthController astraDepthController;
  private HybridPersonTracker hybridPersonTracker;
  private FollowMovementController movementController;

  private ExecutorService visionExecutor = Executors.newSingleThreadExecutor();

  private TextView tvStatus;
  private View vTargetLock;
  private FaceOverlayView faceOverlay;
  private TextureView cameraPreview;

  private float lastPersonX = 0.5f;

  private final Handler checkStatusHandler = new Handler(Looper.getMainLooper());
  private final Runnable statusRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (movementController != null) {
            movementController.checkTargetStatus();
          }
          checkStatusHandler.postDelayed(this, 500);
        }
      };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    register(FollowActivity.class);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_follow);
    initUI();
  }

  private void initUI() {
    tvStatus = findViewById(R.id.tv_follow_status);
    vTargetLock = findViewById(R.id.v_target_lock);
    faceOverlay = findViewById(R.id.face_overlay);
    cameraPreview = findViewById(R.id.camera_preview);
    findViewById(R.id.btn_back).setOnClickListener(v -> finish());
  }

  private void initLogic() {
    // v44: Forçar parada de qualquer stream anterior para evitar "Connection Refused"
    if (hdCameraManager != null) {
      hdCameraManager.closeStream(0);
      hdCameraManager.closeStream(1);
    }

    // Tracker híbrido (Rosto + Astra)
    hybridPersonTracker =
        new HybridPersonTracker(
            this,
            new HybridPersonTracker.HybridTrackingListener() {
              @Override
              public void onPersonDetected(
                  float normalizedX, float distanceMeters, Rect faceBoundingBox) {
                runOnUiThread(() -> faceOverlay.updateFace(faceBoundingBox, distanceMeters));
                updateTrackingState(normalizedX, distanceMeters);
              }

              @Override
              public void onPersonLost() {
                Log.w(TAG, "onPersonLost: Alvo perdido!");
                runOnUiThread(
                    () -> {
                      faceOverlay.clearFace();
                      vTargetLock.setVisibility(View.INVISIBLE);
                      tvStatus.setText("BUSCANDO ALVO...");
                    });
              }
            });

    // Controller para receber imagem YUV (H.264)
    cameraController =
        new CameraController(
            hdCameraManager,
            VISION_WIDTH,
            VISION_HEIGHT,
            (yuvData, width, height) -> {
              if (hybridPersonTracker != null
                  && visionExecutor != null
                  && !visionExecutor.isShutdown()) {
                // v43: Controle de concorrência antes de entrar na fila
                if (hybridPersonTracker.isProcessing()) {
                  return;
                }

                // v43: CLONAR O ARRAY é vital! O original é reutilizado pelo decodificador.
                hybridPersonTracker.setProcessing(true);
                final byte[] clonedData = yuvData.clone();
                visionExecutor.execute(
                    () -> {
                      hybridPersonTracker.processRgbFrame(clonedData, width, height);
                    });
              }
            });

    // Controller para receber profundidade
    astraDepthController =
        new AstraDepthController(
            this,
            (depthData, width, height) -> {
              if (hybridPersonTracker != null) {
                hybridPersonTracker.updateDepthFrame(depthData, width, height);
              }
            });

    cameraController.start();
    astraDepthController.start();
    checkStatusHandler.post(statusRunnable);
  }

  private void updateTrackingState(float personX, float distanceMeters) {
    Log.d(TAG, String.format("updateTrackingState: x=%.2f, dist=%.2f", personX, distanceMeters));
    // Suavização simples para evitar saltos visuais na tela
    lastPersonX = (lastPersonX * 0.7f) + (personX * 0.3f);

    updateTargetIndicator(lastPersonX);
    updateUIStatus(String.format("ALVO TRAVADO (%.1fm)", distanceMeters));

    if (movementController != null) {
      movementController.updateTarget(lastPersonX, distanceMeters);
    }
  }

  private void updateTargetIndicator(float x) {
    runOnUiThread(
        () -> {
          vTargetLock.setVisibility(View.VISIBLE);
          vTargetLock.setX(x * cameraPreview.getWidth() - vTargetLock.getWidth() / 2f);
        });
  }

  private void updateUIStatus(String status) {
    runOnUiThread(
        () -> {
          if (tvStatus != null) {
            tvStatus.setText(status);
          }
        });
  }

  @Override
  public void onMainServiceConnected() {
    hdCameraManager = (HDCameraManager) getUnitManager(FuncConstant.HDCAMERA_MANAGER);
    wheelManager = (WheelMotionManager) getUnitManager(FuncConstant.WHEELMOTION_MANAGER);
    headManager = (HeadMotionManager) getUnitManager(FuncConstant.HEADMOTION_MANAGER);
    speechManager = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);

    movementController = new FollowMovementController(wheelManager, headManager);

    initLogic();
    speechManager.startSpeak("Modo de rastreamento híbrido ativado.");
  }

  @Override
  protected void onStop() {
    super.onStop();
    cleanup();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    cleanup();
  }

  private void cleanup() {
    checkStatusHandler.removeCallbacks(statusRunnable);
    if (visionExecutor != null) {
      visionExecutor.shutdownNow();
      visionExecutor = null;
    }
    if (cameraController != null) {
      cameraController.stop();
      cameraController = null;
    }
    if (astraDepthController != null) {
      astraDepthController.stop();
      astraDepthController = null;
    }
    if (hybridPersonTracker != null) {
      hybridPersonTracker.stop();
      hybridPersonTracker = null;
    }
    if (movementController != null) {
      movementController.stopRobot();
    }
  }
}
