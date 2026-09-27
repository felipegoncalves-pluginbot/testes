package com.felipe.elftemplate.tracking;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

/**
 * Processador assíncrono MoveNet SinglePose Lightning (TensorFlow Lite).
 * 100% offline, Apache 2.0. Landmarks e PoseFrame pré-alocados; 1 thread no RK3288.
 */
public class TFLitePoseProcessor {

  private static final String TAG = "TFLitePoseProcessor";
  private static final String MODEL_ASSET = "movenet_lightning.tflite";
  public static final int MODEL_INPUT_SIZE = 192;
  private static final long POSE_STUCK_MS = 2500L;

  private Interpreter tflite;
  private CameraController cameraController;
  private ExecutorService visionExecutor;
  private final AtomicReference<PoseFrame> latestPoseFrame = new AtomicReference<>();
  private final AtomicBoolean isPoseProcessing = new AtomicBoolean(false);
  private final AtomicBoolean rgbPending = new AtomicBoolean(false);

  private byte[] rgbWorkBuffer;
  private int pendingW = 0;
  private int pendingH = 0;
  private long poseProcessingSinceMs = 0L;

  private ByteBuffer inputBuffer;
  private DataType inputDataType = DataType.INT32;
  private final float[][][][] outputArray = new float[1][1][PoseFrame.KEYPOINT_COUNT][3];
  private final PoseLandmarkData[] preallocatedLandmarks = PoseLandmarkData.allocateKeypoints();
  private final PoseFrame reusedPoseFrame = new PoseFrame(preallocatedLandmarks, 0L, 0, 0);
  private final Nv21LumaEnhancer lumaEnhancer = new Nv21LumaEnhancer();
  private int lastLumaSpan = 0;
  private final RgbPreviewBridge previewBridge = new RgbPreviewBridge();
  private volatile boolean released;

  public void setRgbPreviewListener(RgbPreviewBridge.Listener listener) {
    previewBridge.setListener(listener);
  }

  public void init(Context context, HDCameraManager hdCameraManager) {
    init(context, hdCameraManager, CameraController.StreamProfile.POSE_EFFICIENT);
  }

  public void init(
      Context context,
      HDCameraManager hdCameraManager,
      CameraController.StreamProfile profile) {
    initInterpreter(context);
    visionExecutor = Executors.newSingleThreadExecutor();
    if (hdCameraManager != null) {
      cameraController =
          new CameraController(
              hdCameraManager,
              profile,
              (yuvData, width, height) -> enqueueRgbFrame(yuvData, width, height));
      cameraController.start();
    }
  }

  void initInterpreter(Context context) {
    if (context == null) {
      return;
    }
    try {
      MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_ASSET);
      Interpreter.Options options = new Interpreter.Options();
      options.setNumThreads(1);
      options.setUseNNAPI(false); // Estável em Android 6 API 23 RK3288
      tflite = new Interpreter(modelBuffer, options);

      inputDataType = tflite.getInputTensor(0).dataType();
      int bytesPerChannel = inputDataType == DataType.UINT8 ? 1 : 4;
      inputBuffer = ByteBuffer.allocateDirect(1 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3 * bytesPerChannel);
      inputBuffer.order(ByteOrder.nativeOrder());
      Log.i(TAG, "MoveNet TFLite inicializado com sucesso. DataType: " + inputDataType);
    } catch (Throwable t) {
      Log.e(TAG, "Falha ao carregar MoveNet TFLite: " + t.getMessage(), t);
    }
  }

  private static MappedByteBuffer loadModelFile(Context context, String modelFilename) throws IOException {
    AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFilename);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  private void enqueueRgbFrame(byte[] yuvData, int width, int height) {
    if (released) {
      return;
    }
    ExecutorService executor = visionExecutor;
    if (executor == null || executor.isShutdown()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (isPoseProcessing.get() && poseProcessingSinceMs > 0 && now - poseProcessingSinceMs > POSE_STUCK_MS) {
      isPoseProcessing.set(false);
      poseProcessingSinceMs = 0L;
    }

    int copyLen = yuvData.length;
    if (rgbWorkBuffer == null || rgbWorkBuffer.length < copyLen) {
      rgbWorkBuffer = new byte[copyLen];
    }
    System.arraycopy(yuvData, 0, rgbWorkBuffer, 0, copyLen);
    previewBridge.offer(yuvData, width, height, copyLen);
    pendingW = width;
    pendingH = height;
    rgbPending.set(true);

    if (isPoseProcessing.compareAndSet(false, true)) {
      poseProcessingSinceMs = now;
      executor.execute(this::drainRgbQueue);
    }
  }

  private void drainRgbQueue() {
    if (released || !rgbPending.get()) {
      isPoseProcessing.set(false);
      poseProcessingSinceMs = 0L;
      return;
    }
    rgbPending.set(false);
    try {
      if (!released && tflite != null && inputBuffer != null) {
        processRgb(rgbWorkBuffer, pendingW, pendingH);
      }
    } catch (Throwable t) {
      Log.e(TAG, "Erro na inferência MoveNet: " + t.getMessage(), t);
    } finally {
      isPoseProcessing.set(false);
      poseProcessingSinceMs = 0L;
      if (!released && rgbPending.get() && visionExecutor != null && !visionExecutor.isShutdown()) {
        if (isPoseProcessing.compareAndSet(false, true)) {
          poseProcessingSinceMs = System.currentTimeMillis();
          visionExecutor.execute(this::drainRgbQueue);
        }
      }
    }
  }

  public void processRgb(byte[] yuvData, int srcW, int srcH) {
    if (released || tflite == null || inputBuffer == null || yuvData == null || srcW <= 0 || srcH <= 0) {
      return;
    }
    Nv21LumaEnhancer.LumaStats rawLuma = lumaEnhancer.measure(yuvData, srcW, srcH);
    if (lumaEnhancer.needsEnhancement(rawLuma)) {
      lumaEnhancer.enhanceInPlace(yuvData, srcW, srcH);
    }
    lastLumaSpan = rawLuma.span;
    reusedPoseFrame.setLuma(rawLuma.mean, rawLuma.span);
    reusedPoseFrame.setLightingHold(false);
    convertNV21ToRGBTensor(yuvData, srcW, srcH, inputBuffer, inputDataType);
    tflite.run(inputBuffer, outputArray);

    for (int i = 0; i < PoseFrame.KEYPOINT_COUNT; i++) {
      float y = outputArray[0][0][i][0];
      float x = opticalXFromMoveNet(outputArray[0][0][i][1]);
      if (x < 0.0f) {
        x = 0.0f;
      } else if (x > 1.0f) {
        x = 1.0f;
      }
      float score = outputArray[0][0][i][2];
      preallocatedLandmarks[i].set(x, y, score);
    }

    reusedPoseFrame.update(System.currentTimeMillis(), srcW, srcH);
    latestPoseFrame.set(reusedPoseFrame);
  }

  /**
   * MoveNet x é o eixo óptico da imagem (0 = esquerda do sensor), igual à Astra.
   * Não inverter aqui: o swap L/R anatômico vive só em PoseDepthFusion. Um 1-x
   * extra espelha duas vezes. Calibração de selfie muda ESTE método e o teste de contrato.
   */
  public static float opticalXFromMoveNet(float modelX) {
    return modelX;
  }

  /**
   * Compat testes: delega ao enhancer CLAHE (stretch global só se span &lt; 16).
   *
   * @return média de luma após enhancer
   */
  int stretchNv21Luma(byte[] yuv, int w, int h) {
    Nv21LumaEnhancer.LumaStats raw = lumaEnhancer.measure(yuv, w, h);
    if (lumaEnhancer.needsEnhancement(raw)) {
      lumaEnhancer.enhanceInPlace(yuv, w, h);
    }
    lastLumaSpan = raw.span;
    return raw.mean;
  }

  public static void convertNV21ToRGBTensor(
      byte[] yuv, int srcW, int srcH, ByteBuffer buffer, DataType dtype) {
    buffer.rewind();
    boolean isInt32 = dtype == DataType.INT32;
    boolean isFloat = dtype == DataType.FLOAT32;

    for (int y = 0; y < MODEL_INPUT_SIZE; y++) {
      int srcY = (y * srcH) / MODEL_INPUT_SIZE;
      int yOffset = srcY * srcW;
      int uvRowOffset = srcW * srcH + (srcY >> 1) * srcW;
      for (int x = 0; x < MODEL_INPUT_SIZE; x++) {
        int srcX = (x * srcW) / MODEL_INPUT_SIZE;
        int yVal = yuv[yOffset + srcX] & 0xFF;
        int uvIdx = uvRowOffset + (srcX & ~1);
        int v = (yuv[uvIdx] & 0xFF) - 128;
        int u = (yuv[uvIdx + 1] & 0xFF) - 128;

        int r = (int) (yVal + 1.402f * v);
        int g = (int) (yVal - 0.34414f * u - 0.71414f * v);
        int b = (int) (yVal + 1.772f * u);

        if (r < 0) r = 0; else if (r > 255) r = 255;
        if (g < 0) g = 0; else if (g > 255) g = 255;
        if (b < 0) b = 0; else if (b > 255) b = 255;

        if (isInt32) {
          buffer.putInt(r);
          buffer.putInt(g);
          buffer.putInt(b);
        } else if (isFloat) {
          buffer.putFloat(r);
          buffer.putFloat(g);
          buffer.putFloat(b);
        } else {
          buffer.put((byte) r);
          buffer.put((byte) g);
          buffer.put((byte) b);
        }
      }
    }
  }

  public PoseFrame getLatestPoseFrame() {
    return latestPoseFrame.get();
  }

  public void release() {
    released = true;
    if (cameraController != null) {
      cameraController.stop();
      cameraController = null;
    }
    previewBridge.clear();
    latestPoseFrame.set(null);
    isPoseProcessing.set(false);
    rgbPending.set(false);
    rgbWorkBuffer = null;
    if (visionExecutor != null) {
      visionExecutor.shutdownNow();
      try {
        visionExecutor.awaitTermination(250L, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      visionExecutor = null;
    }
    if (tflite != null) {
      tflite.close();
      tflite = null;
    }
    inputBuffer = null;
  }
}
