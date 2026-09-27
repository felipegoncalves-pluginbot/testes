package com.felipe.elftemplate.tracking;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import java.nio.ByteBuffer;

/**
 * H.264 → YUV. Slot latest-wins (cópia do buffer do SDK) e espera IDR após drop, no padrão
 * MediaCodec / NVR: nunca decodificar GOP velho.
 */
public class VisionMediaDecoder extends Thread {

  private static final String TAG = "VisionMediaDecoder";
  private static final long DROP_LOG_MIN_MS = 2000L;

  private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
  private final LatestEncodedSlot encodedSlot = new LatestEncodedSlot();
  private final boolean[] droppedFlag = new boolean[1];
  private final FrameCallback callback;
  private final int width;
  private final int height;
  private final byte[] yuvBuffer;
  private byte[] nalCopy = new byte[64 * 1024];
  private MediaCodec videoDecoder;
  private volatile boolean isRunning = true;
  private boolean needIdr = true;
  private long lastDropLogMs;
  private int dropsSinceLog;

  public interface FrameCallback {
    void onFrameDecoded(byte[] yuvData, int length, int width, int height);
  }

  public VisionMediaDecoder(int width, int height, FrameCallback callback) {
    this.width = width;
    this.height = height;
    this.callback = callback;
    this.yuvBuffer = new byte[(int) (width * height * 1.5) + 1024];
  }

  public void feedData(byte[] data) {
    encodedSlot.offer(data);
  }

  public void stopDecoder() {
    isRunning = false;
    interrupt();
    try {
      join(400L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void run() {
    setPriority(Thread.NORM_PRIORITY + 1);
    try {
      MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
      videoDecoder = MediaCodec.createDecoderByType("video/avc");
      videoDecoder.configure(format, null, null, 0);
      videoDecoder.start();

      ByteBuffer[] inputBuffers = videoDecoder.getInputBuffers();
      ByteBuffer[] outputBuffers = videoDecoder.getOutputBuffers();

      while (isRunning) {
        droppedFlag[0] = false;
        int n = encodedSlot.copyTo(nalCopy, droppedFlag);
        if (n < 0) {
          nalCopy = new byte[-n];
          n = encodedSlot.copyTo(nalCopy, droppedFlag);
        }
        if (n > 0) {
          if (droppedFlag[0]) {
            noteDrop();
          }
          boolean hasIdr = H264NalUtil.containsIdr(nalCopy, n);
          boolean hasConfig = H264NalUtil.containsSpsOrPps(nalCopy, n);
          // Só espera IDR na abertura. GOP do MAIN_STREAM Sanbot é longo (~8–12s);
          // pular P-frames depois de drop congela o preview até o próximo I-frame.
          if (!(needIdr && !hasIdr && !hasConfig)) {
            int inIndex = videoDecoder.dequeueInputBuffer(2000);
            if (inIndex >= 0) {
              ByteBuffer buffer = inputBuffers[inIndex];
              buffer.clear();
              int cap = buffer.remaining();
              int put = n <= cap ? n : cap;
              buffer.put(nalCopy, 0, put);
              videoDecoder.queueInputBuffer(inIndex, 0, put, System.nanoTime() / 1000, 0);
              if (hasIdr) {
                needIdr = false;
              }
            }
          }
        }

        int outIndex = videoDecoder.dequeueOutputBuffer(bufferInfo, n > 0 ? 0 : 2000);
        while (outIndex >= 0) {
          ByteBuffer outBuffer = outputBuffers[outIndex];
          int size = bufferInfo.size;
          if (size > 0 && callback != null && isRunning) {
            if (yuvBuffer.length >= size) {
              outBuffer.get(yuvBuffer, 0, size);
              outBuffer.clear();
              callback.onFrameDecoded(yuvBuffer, size, width, height);
            }
          }
          videoDecoder.releaseOutputBuffer(outIndex, false);
          outIndex = videoDecoder.dequeueOutputBuffer(bufferInfo, 0);
        }

        if (outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
          outputBuffers = videoDecoder.getOutputBuffers();
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Erro no decodificador", e);
    } finally {
      if (videoDecoder != null) {
        try {
          videoDecoder.stop();
          videoDecoder.release();
        } catch (Exception e) {
          Log.e(TAG, "Erro ao liberar decoder", e);
        }
      }
    }
  }

  private void noteDrop() {
    dropsSinceLog++;
    long now = System.currentTimeMillis();
    if (now - lastDropLogMs < DROP_LOG_MIN_MS) {
      return;
    }
    lastDropLogMs = now;
    Log.w(TAG, "H.264 drop latest-wins x" + dropsSinceLog + " (aguardando IDR)");
    dropsSinceLog = 0;
  }
}
