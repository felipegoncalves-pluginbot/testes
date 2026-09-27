package com.sanbot.debug;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hub de debug para agentes: JSON + PNG Astra + JPEG da câmera HD, a ≤2 Hz.
 * Thread de visão só copia; encode/IO no executor próprio.
 */
public final class SanbotDebugHub {

  public static final int PORT = 8090;
  public static final String DIR_NAME = "sanbot-debug";
  private static final String TAG = "SanbotDebug";
  private static final long MIN_FLUSH_MS = 450L;
  private static final int RGB_JPEG_QUALITY = 55;

  private static final SanbotDebugHub INSTANCE = new SanbotDebugHub();

  private final DebugPose live = new DebugPose();
  private final DebugPose flushPose = new DebugPose();
  private final DebugSensors sensors = new DebugSensors();
  private final DebugSensors flushSensors = new DebugSensors();
  private final StringBuilder jsonBuf = new StringBuilder(2048);
  private final SanbotDebugRenderer renderer = new SanbotDebugRenderer();
  private final AtomicBoolean flushing = new AtomicBoolean(false);
  private final Object visionLock = new Object();
  private final Object rgbLock = new Object();
  private final Object jsonLock = new Object();

  private ExecutorService executor;
  private SanbotDebugServer server;
  private File dir;
  private File jsonFile;
  private File pngFile;
  private File rgbFile;
  private Bitmap visionBmp;
  private Bitmap rgbBmp;
  private int[] rgbArgb;
  private long lastFlushMs;
  private long lastRgbMs;
  private boolean rgbDirty;
  private volatile boolean running;

  public static SanbotDebugHub get() {
    return INSTANCE;
  }

  public synchronized void start(Context context) {
    if (running || context == null) {
      return;
    }
    dir = new File(context.getCacheDir(), DIR_NAME);
    if (!dir.exists() && !dir.mkdirs()) {
      Log.w(TAG, "não criou " + dir);
    }
    jsonFile = new File(dir, "state.json");
    pngFile = new File(dir, "vision.png");
    rgbFile = new File(dir, "rgb.jpg");
    int w = SanbotDebugRenderer.WIDTH;
    int h = SanbotDebugRenderer.HEIGHT;
    if (visionBmp == null) {
      visionBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    }
    if (rgbBmp == null) {
      rgbBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
      rgbArgb = new int[w * h];
    }
    executor = Executors.newSingleThreadExecutor();
    try {
      server = new SanbotDebugServer(this, PORT);
      server.start();
      Log.i(TAG, "HTTP debug em 0.0.0.0:" + PORT);
    } catch (Exception e) {
      Log.e(TAG, "HTTP debug falhou: " + e.getMessage());
      server = null;
    }
    running = true;
  }

  public synchronized void stop() {
    running = false;
    if (server != null) {
      server.stop();
      server = null;
    }
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }

  public DebugPose live() {
    return live;
  }

  public File jsonFile() {
    return jsonFile;
  }

  public File pngFile() {
    return pngFile;
  }

  public File rgbFile() {
    return rgbFile;
  }

  /** Cópia rasa no caller. Encode no máximo a cada {@link #MIN_FLUSH_MS}. */
  public void publish(DebugPose pose, Bitmap silhouette) {
    if (!running || pose == null) {
      return;
    }
    synchronized (live) {
      live.copyFrom(pose);
    }
    long now = System.currentTimeMillis();
    if (now - lastFlushMs < MIN_FLUSH_MS) {
      return;
    }
    lastFlushMs = now;
    synchronized (visionLock) {
      synchronized (live) {
        renderer.draw(visionBmp, live, silhouette);
      }
    }
    requestFlush();
  }

  /**
   * Câmera HD (NV21). Downsample 160×120 no caller (≤2 Hz); JPEG no executor. Sem {@code new}
   * depois do {@link #start}.
   */
  public void offerRgb(byte[] nv21, int width, int height) {
    if (!running || nv21 == null || width <= 0 || height <= 0 || rgbArgb == null) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastRgbMs < MIN_FLUSH_MS) {
      return;
    }
    lastRgbMs = now;
    synchronized (rgbLock) {
      SanbotDebugRgb.fillArgbDownsample(
          nv21,
          width,
          height,
          rgbArgb,
          SanbotDebugRenderer.WIDTH,
          SanbotDebugRenderer.HEIGHT);
      rgbDirty = true;
    }
    synchronized (sensors) {
      sensors.rgbMs = now;
    }
    requestFlush();
  }

  public void onIr(int part, int cm) {
    synchronized (sensors) {
      sensors.setIr(part, cm);
    }
  }

  public void onGyro(float yaw, float pitch, float roll) {
    synchronized (sensors) {
      sensors.gyroYaw = yaw;
      sensors.gyroPitch = pitch;
      sensors.gyroRoll = roll;
      sensors.gyroMs = System.currentTimeMillis();
    }
  }

  public void onPir(boolean checked, int part) {
    synchronized (sensors) {
      if (part == 1) {
        sensors.pirFront = checked;
      } else if (part == 2) {
        sensors.pirRear = checked;
      }
      sensors.pirMs = System.currentTimeMillis();
    }
  }

  public void onTouch(int part) {
    synchronized (sensors) {
      sensors.touchPart = part;
      sensors.touchMs = System.currentTimeMillis();
    }
  }

  public void onVoice(int deg) {
    synchronized (sensors) {
      sensors.voiceDeg = deg;
    }
  }

  public void onObstacle(boolean hit) {
    synchronized (sensors) {
      sensors.obstacle = hit;
    }
  }

  public String snapshotJson() {
    synchronized (live) {
      synchronized (sensors) {
        synchronized (jsonLock) {
          jsonBuf.setLength(0);
          appendStateJson(live, sensors);
          return jsonBuf.toString();
        }
      }
    }
  }

  private void requestFlush() {
    if (flushing.compareAndSet(false, true) && executor != null) {
      executor.execute(this::flushToDisk);
    }
  }

  private void flushToDisk() {
    try {
      synchronized (live) {
        flushPose.copyFrom(live);
      }
      synchronized (sensors) {
        flushSensors.copyFrom(sensors);
      }
      synchronized (jsonLock) {
        jsonBuf.setLength(0);
        appendStateJson(flushPose, flushSensors);
        writeUtf8(jsonFile, jsonBuf.toString());
      }
      if (visionBmp != null && pngFile != null) {
        synchronized (visionLock) {
          FileOutputStream out = new FileOutputStream(pngFile);
          visionBmp.compress(Bitmap.CompressFormat.PNG, 80, out);
          out.close();
        }
      }
      flushRgbJpeg();
    } catch (Exception e) {
      Log.w(TAG, "flush: " + e.getMessage());
    } finally {
      flushing.set(false);
    }
  }

  private void flushRgbJpeg() throws Exception {
    if (rgbBmp == null || rgbFile == null || rgbArgb == null) {
      return;
    }
    synchronized (rgbLock) {
      if (!rgbDirty) {
        return;
      }
      rgbBmp.setPixels(
          rgbArgb,
          0,
          SanbotDebugRenderer.WIDTH,
          0,
          0,
          SanbotDebugRenderer.WIDTH,
          SanbotDebugRenderer.HEIGHT);
      rgbDirty = false;
    }
    FileOutputStream out = new FileOutputStream(rgbFile);
    rgbBmp.compress(Bitmap.CompressFormat.JPEG, RGB_JPEG_QUALITY, out);
    out.close();
  }

  private void appendStateJson(DebugPose pose, DebugSensors sense) {
    pose.appendJson(jsonBuf);
    if (jsonBuf.length() > 0 && jsonBuf.charAt(jsonBuf.length() - 1) == '}') {
      jsonBuf.setLength(jsonBuf.length() - 1);
    }
    jsonBuf.append(",\"sensors\":");
    sense.appendJson(jsonBuf);
    jsonBuf.append('}');
  }

  private static void writeUtf8(File file, String text) throws Exception {
    if (file == null) {
      return;
    }
    FileOutputStream out = new FileOutputStream(file);
    out.write(text.getBytes("UTF-8"));
    out.close();
  }
}
