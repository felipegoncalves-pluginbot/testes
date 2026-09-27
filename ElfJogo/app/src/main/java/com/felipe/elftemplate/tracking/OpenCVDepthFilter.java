package com.felipe.elftemplate.tracking;

import android.util.Log;
import java.nio.ByteBuffer;

/**
 * Invólucro JNI para filtros de profundidade 16-bit acelerados via OpenCV 4.9.0 C++ NDK.
 */
public final class OpenCVDepthFilter {

  private static final String TAG = "OpenCVDepthFilter";
  public static final int MODE_MEDIAN_3X3 = 0;
  public static final int MODE_MORPH_CLOSE = 1;

  private static boolean isNativeLoaded = false;

  static {
    try {
      System.loadLibrary("native-lib");
      isNativeLoaded = true;
    } catch (Throwable t) {
      Log.w(TAG, "OpenCV native-lib não pôde ser carregada: " + t.getMessage());
      isNativeLoaded = false;
    }
  }

  private OpenCVDepthFilter() {}

  public static boolean isAvailable() {
    return isNativeLoaded;
  }

  public static boolean filterDepth(ByteBuffer directBuffer, int width, int height, int filterMode) {
    if (!isNativeLoaded || directBuffer == null || width <= 0 || height <= 0) {
      return false;
    }
    if (!directBuffer.isDirect()) {
      return false;
    }
    try {
      return filterDepthNative(directBuffer, width, height, filterMode);
    } catch (Throwable t) {
      Log.e(TAG, "Falha na filtragem nativa OpenCV: " + t.getMessage());
      return false;
    }
  }

  private static native boolean filterDepthNative(
      ByteBuffer depthDirectBuffer, int width, int height, int filterMode);
}
