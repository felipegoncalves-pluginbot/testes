package com.felipe.elftemplate.tracking;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Instrumentação de debug. Desligada no device: alocava strings + 61MB de log e o ART matava o processo. */
final class DebugTrace {

  private static final boolean ENABLED = false;
  private static final String TAG = "DBG938fa7";
  private static final String SESSION = "938fa7";
  private static final long THROTTLE_MS = 350L;

  private static File logFile;
  private static long lastThrottleMs = 0L;

  private DebugTrace() {}

  static void init(Context context) {
    if (context != null) {
      logFile = new File(context.getCacheDir(), "debug-938fa7.log");
      if (logFile.exists() && logFile.length() > 1000000L) {
        logFile.delete();
      }
    }
  }

  static void log(String hypothesisId, String location, String message, String dataJson) {
    log(hypothesisId, location, message, dataJson, false);
  }

  static void logImmediate(String hypothesisId, String location, String message, String dataJson) {
    log(hypothesisId, location, message, dataJson, true);
  }

  private static void log(
      String hypothesisId, String location, String message, String dataJson, boolean immediate) {
    if (!ENABLED) {
      return;
    }
    long now = System.currentTimeMillis();
    if (!immediate && now - lastThrottleMs < THROTTLE_MS) {
      return;
    }
    if (!immediate) {
      lastThrottleMs = now;
    }
    String line =
        "{\"sessionId\":\""
            + SESSION
            + "\",\"hypothesisId\":\""
            + hypothesisId
            + "\",\"location\":\""
            + location
            + "\",\"message\":\""
            + escape(message)
            + "\",\"data\":"
            + dataJson
            + ",\"timestamp\":"
            + now
            + "}";
    Log.i(TAG, line);
    appendFile(line);
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static void appendFile(String line) {
    if (logFile == null) {
      return;
    }
    try {
      FileOutputStream out = new FileOutputStream(logFile, true);
      out.write(line.getBytes("UTF-8"));
      out.write('\n');
      out.close();
    } catch (IOException ignored) {
      // logcat still available
    }
  }
}
