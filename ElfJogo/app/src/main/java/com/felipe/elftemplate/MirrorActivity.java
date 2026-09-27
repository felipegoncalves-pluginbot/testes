package com.felipe.elftemplate;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import com.felipe.elftemplate.debug.SanbotDebugSensors;
import com.felipe.elftemplate.mirror.MirrorSessionController;
import com.felipe.elftemplate.mirror.MirrorSessionSetup;
import com.felipe.elftemplate.movement.RobotGameFeedback;
import com.felipe.elftemplate.tracking.CameraController;
import com.felipe.elftemplate.tracking.KinectDebugOverlayView;
import com.felipe.elftemplate.tracking.Nv21PreviewView;
import com.felipe.elftemplate.tracking.TrackingResult;
import com.sanbot.debug.SanbotDebugHub;
import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.HeadMotionManager;
import com.sanbot.opensdk.function.unit.SpeechManager;
import com.sanbot.opensdk.function.unit.WheelMotionManager;
import com.sanbot.opensdk.function.unit.WingMotionManager;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modo Espelho / Marionete: tela cheia com câmera HD + esqueleto. Orquestração em {@link
 * MirrorSessionController}; o tracking métrico 3D é montado por {@link MirrorSessionSetup}.
 */
public class MirrorActivity extends BindBaseActivity {

  private Nv21PreviewView cameraPreview;
  private KinectDebugOverlayView kinectOverlay;

  private MirrorSessionSetup.Bundle mirrorBundle;
  private MirrorSessionController sessionController;
  private RobotGameFeedback robotFeedback;
  private final AtomicBoolean overlayPosted = new AtomicBoolean(false);
  private volatile TrackingResult pendingResult;
  private volatile Bitmap pendingSilhouette;
  private volatile float pendingRgbPan;
  private volatile boolean stopped;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    register(MirrorActivity.class);
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_mirror);

    initUI();
    mirrorBundle = MirrorSessionSetup.createMetric3d();
    sessionController = new MirrorSessionController(mirrorBundle.tracking.getProvider());
    sessionController.setOverlayListener(this::enqueueOverlay);
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      enterImmersiveFullscreen();
    }
  }

  private void enterImmersiveFullscreen() {
    View decor = getWindow().getDecorView();
    decor.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }

  private void initUI() {
    cameraPreview = findViewById(R.id.camera_preview);
    cameraPreview.setMirrored(true);
    kinectOverlay = findViewById(R.id.kinect_debug_overlay);
    kinectOverlay.setMinimalFullscreen(true);
    findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    kinectOverlay.setOnClickListener(v -> finish());
  }

  @Override
  public void onMainServiceConnected() {
    SpeechManager speech = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);
    WingMotionManager wing = (WingMotionManager) getUnitManager(FuncConstant.WINGMOTION_MANAGER);
    HeadMotionManager head = (HeadMotionManager) getUnitManager(FuncConstant.HEADMOTION_MANAGER);
    WheelMotionManager wheel =
        (WheelMotionManager) getUnitManager(FuncConstant.WHEELMOTION_MANAGER);
    HardWareManager hardware = (HardWareManager) getUnitManager(FuncConstant.HARDWARE_MANAGER);

    robotFeedback = new RobotGameFeedback(speech, wing, head, wheel, hardware);
    robotFeedback.resetWings();
    robotFeedback.resetCenter();
    sessionController.setRobotFeedback(robotFeedback);
    SanbotDebugSensors.bind(hardware);
    SanbotDebugHub.get().start(this);
    mirrorBundle.startRelay();
    robotFeedback.speak(
        "Modo Espelho ativado! Eu sou o seu reflexo! Mova os braços e a cabeça para eu te imitar!");

    startMirrorTracking();
  }

  private void startMirrorTracking() {
    HDCameraManager hdCamera = (HDCameraManager) getUnitManager(FuncConstant.HDCAMERA_MANAGER);
    sessionController.prepareFullscreenSession();
    mirrorBundle.tracking.attachHdCamera(hdCamera);
    sessionController.setRgbPreviewListener(
        (nv21, width, height) -> {
          if (cameraPreview != null) {
            cameraPreview.updateFrame(nv21, width, height);
          }
          SanbotDebugHub.get().offerRgb(nv21, width, height);
        });
    sessionController.start(this, CameraController.StreamProfile.POSE_EFFICIENT);
  }

  private void enqueueOverlay(TrackingResult result, Bitmap silhouette, float rgbPanNorm) {
    if (stopped) {
      return;
    }
    pendingResult = result;
    pendingSilhouette = silhouette;
    pendingRgbPan = rgbPanNorm;
    if (overlayPosted.compareAndSet(false, true)) {
      runOnUiThread(this::drainOverlay);
    }
  }

  private void drainOverlay() {
    overlayPosted.set(false);
    if (stopped) {
      return;
    }
    TrackingResult result = pendingResult;
    Bitmap silhouette = pendingSilhouette;
    if (kinectOverlay != null) {
      kinectOverlay.setRgbPanNorm(pendingRgbPan);
      kinectOverlay.updateTracking(result, silhouette);
    }
    if (pendingResult != result && overlayPosted.compareAndSet(false, true)) {
      runOnUiThread(this::drainOverlay);
    }
  }

  @Override
  protected void onStop() {
    stopped = true;
    if (mirrorBundle != null) {
      mirrorBundle.stopRelay();
    }
    if (sessionController != null) {
      sessionController.stop();
    }
    super.onStop();
  }
}
