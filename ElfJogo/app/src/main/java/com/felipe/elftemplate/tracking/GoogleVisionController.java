package com.felipe.elftemplate.tracking;

import android.util.Log;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controlador para Google ML Kit (Vision API). Compatível com Android 6. Usado para detecção de
 * objetos e estabilização de marcos.
 */
public class GoogleVisionController {
  private static final String TAG = "GoogleVision";
  private ObjectDetector objectDetector;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  public GoogleVisionController() {
    init();
  }

  private void init() {
    try {
      // v25: Configuração do ML Kit para modo STREAM (baixa latência)
      ObjectDetectorOptions options =
          new ObjectDetectorOptions.Builder()
              .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
              .enableMultipleObjects()
              .build();

      objectDetector = ObjectDetection.getClient(options);
      Log.i(TAG, "ML Kit Object Detector Initialized (API 23 Compatible)");
    } catch (Exception e) {
      Log.e(TAG, "Erro ao inicializar ML Kit: " + e.getMessage());
    }
  }

  public void processFrame(byte[] yuvData, int width, int height) {
    if (objectDetector == null) return;

    // Converte YUV para InputImage do ML Kit
    InputImage image =
        InputImage.fromByteArray(
            yuvData,
            width,
            height,
            0, // Rotation degrees
            InputImage.IMAGE_FORMAT_NV21);

    // v32: Execute callbacks on background executor to avoid main thread load
    objectDetector
        .process(image)
        .addOnSuccessListener(executor, objects -> {})
        .addOnFailureListener(
            executor,
            e -> {
              Log.e(TAG, "Falha no processamento ML Kit", e);
            });
  }

  public void release() {
    if (objectDetector != null) {
      objectDetector.close();
      objectDetector = null;
    }
    executor.shutdown();
  }
}
