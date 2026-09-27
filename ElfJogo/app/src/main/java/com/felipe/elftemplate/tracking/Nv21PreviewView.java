package com.felipe.elftemplate.tracking;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import com.sanbot.debug.SanbotDebugRgb;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Preview NV21 → ARGB downsample (sem JPEG). Espelho só no canvas, sem {@code View.setScaleX}
 * (camada de hardware no API 23 congela Bitmap mutado).
 */
public class Nv21PreviewView extends View {

  static final int MAX_PREVIEW_W = 640;
  static final int MAX_PREVIEW_H = 360;

  private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final Rect srcRect = new Rect();
  private final Rect dstRect = new Rect();
  private final int[] destLtrb = new int[4];
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final Object frameLock = new Object();
  private final AtomicBoolean decodeBusy = new AtomicBoolean(false);
  private volatile boolean frameWaiting;
  private volatile boolean mirrored;

  private ExecutorService renderExecutor;
  private byte[] frameData;
  private byte[] workNv21;
  private int frameW;
  private int frameH;
  private int[] argbA;
  private int[] argbB;
  private int[] uiArgb;
  private boolean useArgbA = true;
  private int[] readyArgb;
  private int readyW;
  private int readyH;
  private Bitmap displayBitmap;
  private final Runnable decodeTask =
      new Runnable() {
        @Override
        public void run() {
          try {
            byte[] data;
            int w;
            int h;
            synchronized (frameLock) {
              if (frameData == null) {
                return;
              }
              w = frameW;
              h = frameH;
              int len = w * h * 3 / 2;
              if (workNv21 == null || workNv21.length < len) {
                workNv21 = new byte[len];
              }
              System.arraycopy(frameData, 0, workNv21, 0, len);
              data = workNv21;
            }
            renderArgb(data, w, h);
          } finally {
            decodeBusy.set(false);
            if (frameWaiting) {
              frameWaiting = false;
              scheduleDecode();
            }
          }
        }
      };
  private final Runnable publishTask =
      new Runnable() {
        @Override
        public void run() {
          int[] src;
          int w;
          int h;
          synchronized (frameLock) {
            src = readyArgb;
            w = readyW;
            h = readyH;
            if (src == null || w <= 0 || h <= 0) {
              return;
            }
            if (uiArgb == null || uiArgb.length != src.length) {
              uiArgb = new int[src.length];
            }
            System.arraycopy(src, 0, uiArgb, 0, src.length);
          }
          if (displayBitmap == null
              || displayBitmap.getWidth() != w
              || displayBitmap.getHeight() != h
              || displayBitmap.isRecycled()) {
            displayBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
          }
          displayBitmap.setPixels(uiArgb, 0, w, 0, 0, w, h);
          invalidate();
        }
      };

  public Nv21PreviewView(Context context) {
    super(context);
  }

  public Nv21PreviewView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  /** Espelho de palco: {@code canvas.scale(-1, 1)}, sem layer do View. */
  public void setMirrored(boolean mirror) {
    mirrored = mirror;
    invalidate();
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (renderExecutor == null) {
      renderExecutor = Executors.newSingleThreadExecutor();
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    if (renderExecutor != null) {
      renderExecutor.shutdownNow();
      renderExecutor = null;
    }
    mainHandler.removeCallbacks(publishTask);
    super.onDetachedFromWindow();
  }

  public void updateFrame(byte[] nv21, int width, int height) {
    if (nv21 == null || width <= 0 || height <= 0 || renderExecutor == null) {
      return;
    }
    int len = width * height * 3 / 2;
    if (len > nv21.length) {
      return;
    }
    synchronized (frameLock) {
      if (frameData == null || frameData.length < len) {
        frameData = new byte[len];
      }
      System.arraycopy(nv21, 0, frameData, 0, len);
      frameW = width;
      frameH = height;
    }
    scheduleDecode();
  }

  private void scheduleDecode() {
    ExecutorService executor = renderExecutor;
    if (executor == null || executor.isShutdown()) {
      decodeBusy.set(false);
      return;
    }
    if (!decodeBusy.compareAndSet(false, true)) {
      frameWaiting = true;
      return;
    }
    frameWaiting = false;
    executor.execute(decodeTask);
  }

  private void renderArgb(byte[] nv21, int width, int height) {
    int dstW = choosePreviewWidth(width, height);
    int dstH = choosePreviewHeight(width, height);
    if (dstW <= 0 || dstH <= 0) {
      return;
    }
    int[] pixels = useArgbA ? argbA : argbB;
    if (pixels == null || pixels.length != dstW * dstH) {
      pixels = new int[dstW * dstH];
      if (useArgbA) {
        argbA = pixels;
      } else {
        argbB = pixels;
      }
    }
    SanbotDebugRgb.fillArgbDownsample(nv21, width, height, pixels, dstW, dstH);
    useArgbA = !useArgbA;
    synchronized (frameLock) {
      readyArgb = pixels;
      readyW = dstW;
      readyH = dstH;
    }
    mainHandler.removeCallbacks(publishTask);
    mainHandler.post(publishTask);
  }

  static int choosePreviewWidth(int srcW, int srcH) {
    float scale = previewScale(srcW, srcH);
    int w = Math.max(2, Math.round(srcW * scale) & ~1);
    return w;
  }

  static int choosePreviewHeight(int srcW, int srcH) {
    float scale = previewScale(srcW, srcH);
    int h = Math.max(2, Math.round(srcH * scale) & ~1);
    return h;
  }

  static float previewScale(int srcW, int srcH) {
    if (srcW <= 0 || srcH <= 0) {
      return 1f;
    }
    float scaleW = (float) MAX_PREVIEW_W / (float) srcW;
    float scaleH = (float) MAX_PREVIEW_H / (float) srcH;
    float scale = Math.min(1f, Math.min(scaleW, scaleH));
    return scale;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int viewW = getWidth();
    int viewH = getHeight();
    if (viewW == 0 || viewH == 0) {
      return;
    }
    Bitmap bmp = displayBitmap;
    if (bmp == null || bmp.isRecycled()) {
      canvas.drawColor(0xFF000000);
      return;
    }
    PreviewViewport.centerCropDest(viewW, viewH, bmp.getWidth(), bmp.getHeight(), destLtrb);
    dstRect.set(destLtrb[0], destLtrb[1], destLtrb[2], destLtrb[3]);
    srcRect.set(0, 0, bmp.getWidth(), bmp.getHeight());
    if (mirrored) {
      canvas.save();
      canvas.translate(viewW, 0f);
      canvas.scale(-1f, 1f);
    }
    canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint);
    if (mirrored) {
      canvas.restore();
    }
  }
}
