package com.felipe.elftemplate.tracking;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Fachada leve de Rastreamento Corporal, Esqueleto 3D e Gestos para Sanbot Elf. */
public class KinectTrackingEngine {

  private static final String TAG = "KinectTrackingEngine";

  public enum GestureType {
    IDLE, SWIPE_LEFT, SWIPE_RIGHT, SWING_UP, T_POSE,
    HANDS_UP, LEFT_HAND_UP, RIGHT_HAND_UP, HEAD_NOD_YES, HEAD_SHAKE_NO, DUCK, JUMP
  }

  public interface TrackingCallback {
    void onTrackingUpdate(TrackingResult result, Bitmap debugSilhouette);
  }

  /** Punhos pós-Holt, antes do EMA de corpo — menor latência para cursor Kinect. */
  public interface LowLatencyHandListener {
    void onHandFrame(TrackingResult result);
  }

  private static final class DepthFrameHolder {
    short[] data;
    int width;
    int height;
  }

  private final DepthFrameHolder[] depthHolders = new DepthFrameHolder[] {
    new DepthFrameHolder(),
    new DepthFrameHolder()
  };
  private int currentHolderIdx = 0;
  private final AtomicBoolean isDepthProcessing = new AtomicBoolean(false);
  private final AtomicReference<DepthFrameHolder> pendingDepthFrame = new AtomicReference<>();

  private AstraDepthController astraController;
  private TrackingCallback callback;
  private volatile boolean isRunning = false;
  private ExecutorService workerExecutor;
  private final TFLitePoseProcessor poseProcessor = new TFLitePoseProcessor();
  private final DepthFrameProcessor depthProcessor = new DepthFrameProcessor();
  private final TrackingModeConfig trackingModeConfig = new TrackingModeConfig();
  private final KinectTrackingSmoother trackingSmoother = new KinectTrackingSmoother();
  private final KinectHoltHandFilter holtHandFilter = new KinectHoltHandFilter();
  private final FusionModeLatch fusionLatch = new FusionModeLatch();
  private final DepthPreviewBridge depthPreviewBridge = new DepthPreviewBridge();
  private final TrackingResult lowLatencyScratch = new TrackingResult();

  private Bitmap debugBitmap;
  private int[] debugPixels;
  private float baselineCentroidY = 0.5f;
  private boolean isBaselineCalibrated = false;
  private boolean depthOverlayTransparent = false;
  private volatile LowLatencyHandListener lowLatencyHandListener;

  public void setLowLatencyHandListener(LowLatencyHandListener listener) {
    lowLatencyHandListener = listener;
  }

  public void setDepthOverlayTransparent(boolean transparent) {
    depthOverlayTransparent = transparent;
  }

  public KinectTrackingEngine() {
    debugPixels = new int[KinectDebugRenderer.DEBUG_BMP_W * KinectDebugRenderer.DEBUG_BMP_H];
    try {
      debugBitmap =
          Bitmap.createBitmap(
              KinectDebugRenderer.DEBUG_BMP_W,
              KinectDebugRenderer.DEBUG_BMP_H,
              Bitmap.Config.ARGB_8888);
    } catch (Throwable ignored) {}
  }

  public TrackingModeConfig getTrackingModeConfig() {
    return trackingModeConfig;
  }

  public void setTrackingMode(TrackingMode mode) {
    trackingModeConfig.setMode(mode);
    if (mode != TrackingMode.SINGLE_STICKY) {
      trackingModeConfig.resetStickyLock();
    }
  }

  public void setRgbPreviewListener(RgbPreviewBridge.Listener listener) {
    poseProcessor.setRgbPreviewListener(listener);
  }

  public void setDepthPreviewListener(DepthPreviewBridge.Listener listener) {
    depthPreviewBridge.setListener(listener);
  }

  public void start(Context context, TrackingCallback callback) {
    start(context, callback, null);
  }

  public void start(Context context, TrackingCallback callback, HDCameraManager hdCameraManager) {
    start(context, callback, hdCameraManager, CameraController.StreamProfile.POSE_EFFICIENT);
  }

  public void start(
      Context context,
      TrackingCallback callback,
      HDCameraManager hdCameraManager,
      CameraController.StreamProfile cameraProfile) {
    DebugTrace.init(context);
    this.callback = callback;
    this.isRunning = true;
    this.workerExecutor = Executors.newSingleThreadExecutor();

    if (hdCameraManager != null) {
      poseProcessor.init(context, hdCameraManager, cameraProfile);
    }

    try {
      astraController =
          new AstraDepthController(
              context,
              (depthData, width, height) -> enqueueDepthFrame(depthData, width, height));
      astraController.start();
    } catch (Throwable t) {
      Log.e(TAG, "Erro ao iniciar Astra: " + t.getMessage(), t);
    }
  }

  private void enqueueDepthFrame(short[] depthData, int width, int height) {
    if (!isRunning || workerExecutor == null || workerExecutor.isShutdown() || depthData == null) {
      return;
    }
    DepthFrameHolder holder = depthHolders[currentHolderIdx];
    currentHolderIdx = (currentHolderIdx + 1) % depthHolders.length;
    if (holder.data == null || holder.data.length < depthData.length) {
      holder.data = new short[depthData.length];
    }
    System.arraycopy(depthData, 0, holder.data, 0, depthData.length);
    holder.width = width;
    holder.height = height;
    depthPreviewBridge.offer(holder.data, width, height);
    pendingDepthFrame.set(holder);

    if (isDepthProcessing.compareAndSet(false, true)) {
      workerExecutor.execute(this::drainDepthQueue);
    }
  }

  private void drainDepthQueue() {
    try {
      while (isRunning) {
        DepthFrameHolder frame = pendingDepthFrame.getAndSet(null);
        if (frame == null) {
          break;
        }
        processDepthFrameInternal(frame.data, frame.width, frame.height);
      }
    } finally {
      isDepthProcessing.set(false);
      if (isRunning && pendingDepthFrame.get() != null && isDepthProcessing.compareAndSet(false, true)) {
        workerExecutor.execute(this::drainDepthQueue);
      }
    }
  }

  private void processDepthFrameInternal(short[] depthData, int width, int height) {
    long startTime = System.currentTimeMillis();
    TrackingResult rawResult = ingestDepthFrame(depthData, width, height);

    float depthLeftHandY = rawResult.leftHand.y;
    float depthRightHandY = rawResult.rightHand.y;

    PoseFrame poseFrame = poseProcessor.getLatestPoseFrame();
    boolean fusedNow = false;
    if (PoseDepthFusion.allowRgbFusion(rawResult)) {
      fusedNow =
          PoseDepthFusion.tryFuse(
              rawResult, poseFrame, depthData, width, height, startTime, fusionLatch.isLatched());
    } else {
      fusionLatch.reset();
    }
    boolean latched = fusionLatch.observe(fusedNow);
    if (fusedNow) {
      PoseDepthHandBlend.blendVerticalWithDepth(rawResult, depthLeftHandY, depthRightHandY);
      rawResult.diagnostics.isPoseFusionActive = true;
    } else if (latched
        && !rawResult.diagnostics.isNearProximityMode
        && trackingSmoother.peek().isPlayerPresent) {
      // Latch só fora da proximidade: em Z<880 mm o blob Astra é a fonte (CV_TRACKING_SPEC).
      rawResult.copySkeletonFrom(trackingSmoother.peek());
      rawResult.diagnostics.isPoseFusionActive = true;
    } else {
      rawResult.diagnostics.isPoseFusionActive = false;
    }

    holtHandFilter.filterHands(rawResult);
    KinectGestureClassifier classifier = depthProcessor.getGestureClassifier();
    ArmElevationBridge.syncFromJoints(rawResult, classifier);
    classifier.classifyPostures(
        rawResult, rawResult.playerCentroidY - baselineCentroidY);

    LowLatencyHandListener handListener = lowLatencyHandListener;
    if (handListener != null) {
      lowLatencyScratch.applyFrom(rawResult);
      handListener.onHandFrame(lowLatencyScratch);
    }

    // #region agent log
    DebugTrace.log(
        "E",
        "KinectTrackingEngine.processDepthFrameInternal",
        "frame_tracking_summary",
        "{\"fused\":"
            + fusedNow
            + ",\"leftPx\":"
            + rawResult.diagnostics.leftHandPixelCount
            + ",\"rightPx\":"
            + rawResult.diagnostics.rightHandPixelCount
            + ",\"lElev\":"
            + rawResult.leftHandElevation
            + ",\"rElev\":"
            + rawResult.rightHandElevation
            + ",\"lHandY\":"
            + rawResult.leftHand.y
            + ",\"lShoulderY\":"
            + rawResult.leftShoulder.y
            + ",\"lRaised\":"
            + rawResult.isLeftHandRaised
            + "}");
    // #endregion

    TrackingResult finalResult = trackingSmoother.apply(rawResult);
    finalResult.diagnostics.processingLatencyMs = System.currentTimeMillis() - startTime;

    // #region agent log
    DebugTrace.log(
        "F",
        "KinectTrackingEngine.processDepthFrameInternal",
        "overlay_skeleton_summary",
        "{\"smoothLHandY\":"
            + finalResult.leftHand.y
            + ",\"smoothLElbowY\":"
            + finalResult.leftElbow.y
            + ",\"smoothLShoulderY\":"
            + finalResult.leftShoulder.y
            + ",\"rawLElbowY\":"
            + rawResult.leftElbow.y
            + "}");
    // #endregion

    KinectDebugRenderer.renderToBitmap(
        depthData, width, height, debugBitmap, debugPixels, depthOverlayTransparent);

    if (callback != null) {
      callback.onTrackingUpdate(finalResult, debugBitmap);
    }
  }

  public TrackingResult processDepthFrame(short[] depthData, int width, int height) {
    TrackingResult result = ingestDepthFrame(depthData, width, height);
    depthProcessor
        .getGestureClassifier()
        .classifyPostures(result, result.playerCentroidY - baselineCentroidY);
    return result;
  }

  private TrackingResult ingestDepthFrame(short[] depthData, int width, int height) {
    TrackingResult result =
        depthProcessor.process(
            depthData,
            width,
            height,
            trackingModeConfig,
            baselineCentroidY,
            isBaselineCalibrated);

    if (result.isPlayerPresent && !isBaselineCalibrated) {
      baselineCentroidY = result.playerCentroidY;
      isBaselineCalibrated = true;
    } else if (result.isPlayerPresent) {
      baselineCentroidY = baselineCentroidY * 0.95f + result.playerCentroidY * 0.05f;
    }
    return result;
  }

  /** Expõe pipeline EMA para testes de regressão do overlay (package-private). */
  TrackingResult applyEmaSmoothingForTest(TrackingResult raw) {
    return trackingSmoother.apply(raw);
  }

  public GestureType updateHandPosition(float x, float y, long timestamp) {
    return depthProcessor
        .getGestureClassifier()
        .updateHandTrajectories(x, y, true, x, y, true, timestamp);
  }

  public void stop() {
    callback = null;
    lowLatencyHandListener = null;
    isRunning = false;
    isBaselineCalibrated = false;
    fusionLatch.reset();
    trackingModeConfig.resetStickyLock();
    depthProcessor.reset();
    depthPreviewBridge.clear();
    
    final ExecutorService oldWorker = workerExecutor;
    workerExecutor = null;
    
    final AstraDepthController oldAstra = astraController;
    astraController = null;
    
    // TFLitePoseProcessor might also take time to release models or threads
    final TFLitePoseProcessor oldPose = poseProcessor;

    new Thread(() -> {
      try {
        oldPose.release();
        if (oldWorker != null) {
          oldWorker.shutdownNow();
        }
        if (oldAstra != null) {
          oldAstra.stop();
        }
      } catch (Throwable t) {
        Log.e(TAG, "Erro no teardown da thread de tracking: " + t.getMessage(), t);
      }
    }, "KinectTeardownThread").start();
  }
}
