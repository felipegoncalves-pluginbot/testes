package com.felipe.elftemplate.tracking;

/**
 * Entrega o NV21 mais recente no thread do decoder (sem hop no Looper). O listener copia na hora;
 * o próximo {@link #offer} pode reutilizar o buffer.
 */
public final class RgbPreviewBridge {

  public interface Listener {
    void onRgbPreviewFrame(byte[] nv21, int width, int height);
  }

  private final Object frameLock = new Object();
  private byte[] frontBuffer;
  private byte[] backBuffer;
  private int frameWidth;
  private int frameHeight;
  private Listener listener;

  public void setListener(Listener target) {
    listener = target;
  }

  public void clear() {
    listener = null;
    synchronized (frameLock) {
      frontBuffer = null;
      backBuffer = null;
      frameWidth = 0;
      frameHeight = 0;
    }
  }

  public void offer(byte[] src, int width, int height, int length) {
    Listener target = listener;
    if (target == null || src == null || width <= 0 || height <= 0 || length <= 0) {
      return;
    }

    byte[] frame;
    int w;
    int h;
    synchronized (frameLock) {
      if (backBuffer == null || backBuffer.length < length) {
        backBuffer = new byte[length];
      }
      System.arraycopy(src, 0, backBuffer, 0, length);
      frameWidth = width;
      frameHeight = height;
      byte[] swap = frontBuffer;
      frontBuffer = backBuffer;
      backBuffer = swap;
      frame = frontBuffer;
      w = frameWidth;
      h = frameHeight;
    }
    target.onRgbPreviewFrame(frame, w, h);
  }
}
