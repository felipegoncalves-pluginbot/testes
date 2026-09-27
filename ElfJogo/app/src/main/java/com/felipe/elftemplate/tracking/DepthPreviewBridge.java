package com.felipe.elftemplate.tracking;

/**
 * Entrega o depth mais recente no thread da Astra. O listener deve copiar na hora; o próximo {@link
 * #offer} pode reutilizar o buffer.
 */
public final class DepthPreviewBridge {

  public interface Listener {
    void onDepthPreviewFrame(short[] depthMm, int width, int height);
  }

  private final Object frameLock = new Object();
  private short[] frontBuffer;
  private short[] backBuffer;
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

  public void offer(short[] src, int width, int height) {
    Listener target = listener;
    if (target == null || src == null || width <= 0 || height <= 0) {
      return;
    }
    int length = width * height;
    if (src.length < length) {
      return;
    }

    short[] frame;
    int w;
    int h;
    synchronized (frameLock) {
      if (backBuffer == null || backBuffer.length < length) {
        backBuffer = new short[length];
      }
      System.arraycopy(src, 0, backBuffer, 0, length);
      frameWidth = width;
      frameHeight = height;
      short[] swap = frontBuffer;
      frontBuffer = backBuffer;
      backBuffer = swap;
      frame = frontBuffer;
      w = frameWidth;
      h = frameHeight;
    }
    target.onDepthPreviewFrame(frame, w, h);
  }
}
