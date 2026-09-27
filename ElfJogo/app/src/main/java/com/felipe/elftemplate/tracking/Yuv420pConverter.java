package com.felipe.elftemplate.tracking;

/** Converte saída planar I420 do MediaCodec Sanbot para NV21 (Android YuvImage). */
public final class Yuv420pConverter {

  private Yuv420pConverter() {}

  public static void toNv21(byte[] yuv420p, byte[] nv21, int width, int height) {
    int frameSize = width * height;
    int qFrameSize = frameSize / 4;
    if (yuv420p == null
        || nv21 == null
        || yuv420p.length < frameSize + 2 * qFrameSize
        || nv21.length < frameSize + 2 * qFrameSize) {
      return;
    }
    System.arraycopy(yuv420p, 0, nv21, 0, frameSize);
    for (int i = 0; i < qFrameSize; i++) {
      nv21[frameSize + i * 2] = yuv420p[frameSize + qFrameSize + i];
      nv21[frameSize + i * 2 + 1] = yuv420p[frameSize + i];
    }
  }
}
