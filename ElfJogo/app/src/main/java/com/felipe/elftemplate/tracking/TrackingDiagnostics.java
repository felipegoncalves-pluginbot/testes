package com.felipe.elftemplate.tracking;

/** Métricas e diagnóstico em tempo real do pipeline de visão computacional e esqueleto. */
public class TrackingDiagnostics {
  public float cameraFps = 0.0f;
  public int frameWidth = 0;
  public int frameHeight = 0;
  public int peakDepthZ = 0;
  public int sliceMinZ = 0;
  public int sliceMaxZ = 0;
  public int validPixelCount = 0;
  public int headPixelCount = 0;
  public int leftHandPixelCount = 0;
  public int rightHandPixelCount = 0;
  public long processingLatencyMs = 0;
  public float bodyAspectHW = 0.0f;
  public boolean isNearProximityMode = false;
  public boolean isSeatedPose = false;
  public boolean isPoseFusionActive = false;
  /** Modo espelho: esqueleto veio do auxiliar WiFi (ML Kit), não do blob local. */
  public boolean isRemoteAuxActive = false;
  public int trackedPersonCount = 0;
  public String trackingMode = TrackingMode.SINGLE_CLOSEST.name();

  /** Sentinela de "este pipeline não informa confiança": mantém o caminho por pixelCount. */
  public static final float CONFIDENCE_NOT_REPORTED = -1.0f;

  /**
   * Confiança do punho, no eixo da imagem: 1 = medido no sensor, ~0,2 = sintetizado por proporção.
   *
   * <p>O pipeline de blob não distinguia mão detectada de mão inventada, e usava contagem de pixels
   * laterais como aproximação. O motor métrico sabe a diferença por junta, então quem consome pode
   * decidir não desenhar nem acionar motor com um braço que ninguém viu. Fica em
   * {@link #CONFIDENCE_NOT_REPORTED} enquanto o produtor não informar.
   */
  public float leftArmConfidence = CONFIDENCE_NOT_REPORTED;

  public float rightArmConfidence = CONFIDENCE_NOT_REPORTED;

  /** Cópia campo a campo, sem alocar, para entregar um retrato do frame a outra thread. */
  public void copyFrom(TrackingDiagnostics src) {
    if (src == null) {
      return;
    }
    cameraFps = src.cameraFps;
    frameWidth = src.frameWidth;
    frameHeight = src.frameHeight;
    peakDepthZ = src.peakDepthZ;
    sliceMinZ = src.sliceMinZ;
    sliceMaxZ = src.sliceMaxZ;
    validPixelCount = src.validPixelCount;
    headPixelCount = src.headPixelCount;
    leftHandPixelCount = src.leftHandPixelCount;
    rightHandPixelCount = src.rightHandPixelCount;
    processingLatencyMs = src.processingLatencyMs;
    bodyAspectHW = src.bodyAspectHW;
    isNearProximityMode = src.isNearProximityMode;
    isSeatedPose = src.isSeatedPose;
    isPoseFusionActive = src.isPoseFusionActive;
    isRemoteAuxActive = src.isRemoteAuxActive;
    trackedPersonCount = src.trackedPersonCount;
    trackingMode = src.trackingMode;
    leftArmConfidence = src.leftArmConfidence;
    rightArmConfidence = src.rightArmConfidence;
  }

  public void reset() {
    cameraFps = 0.0f;
    frameWidth = 0;
    frameHeight = 0;
    peakDepthZ = 0;
    sliceMinZ = 0;
    sliceMaxZ = 0;
    validPixelCount = 0;
    headPixelCount = 0;
    leftHandPixelCount = 0;
    rightHandPixelCount = 0;
    processingLatencyMs = 0;
    bodyAspectHW = 0.0f;
    isNearProximityMode = false;
    isSeatedPose = false;
    isPoseFusionActive = false;
    isRemoteAuxActive = false;
    trackedPersonCount = 0;
    trackingMode = TrackingMode.SINGLE_CLOSEST.name();
    leftArmConfidence = CONFIDENCE_NOT_REPORTED;
    rightArmConfidence = CONFIDENCE_NOT_REPORTED;
  }
}
