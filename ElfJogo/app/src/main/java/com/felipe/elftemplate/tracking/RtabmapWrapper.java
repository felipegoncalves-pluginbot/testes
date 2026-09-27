package com.felipe.elftemplate.tracking;

/** Wrapper JNI para acessar o core em C++ do RTAB-Map. */
public class RtabmapWrapper {

  static {
    // Na execução real, isso deve carregar o librtabmap-jni.so
    // System.loadLibrary("rtabmap-jni");
  }

  private boolean isInitialized = false;

  public RtabmapWrapper() {}

  /** Inicializa a engine do RTAB-Map. */
  public void init() {
    // initNative();
    isInitialized = true;
  }

  /**
   * Processa os dados RGB-D.
   *
   * @param rgb Dados RGB.
   * @param depth Dados de Profundidade.
   * @param width Largura.
   * @param height Altura.
   * @param timestamp Timestamp em ms.
   * @return Array com [x, y, yaw, confidence] da odometria visual 6-DOF simplificada para 2D.
   */
  public float[] processRgbd(byte[] rgb, short[] depth, int width, int height, long timestamp) {
    if (!isInitialized) return new float[] {0, 0, 0, 0};

    // Chamada nativa fake/placeholder
    // return processRgbdNative(rgb, depth, width, height, timestamp);
    return new float[] {0, 0, 0, 0};
  }

  /** Retorna o mapa de ocupação 2D (Costmap) gerado pelo RTAB-Map. */
  public byte[] getGridMap() {
    // return getGridMapNative();
    return new byte[0];
  }

  /** Limpa/Reseta o mapa. */
  public void reset() {
    // resetNative();
  }

  public void stop() {
    isInitialized = false;
    // stopNative();
  }

  /*
  private native void initNative();
  private native float[] processRgbdNative(byte[] rgb, short[] depth, int width, int height, long timestamp);
  private native byte[] getGridMapNative();
  private native void resetNative();
  */
}
