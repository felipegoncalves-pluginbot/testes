package com.felipe.elftemplate.tracking;

/**
 * NAL Annex-B (Sanbot HD). Depois de um drop, só o IDR recupera o GOP — OpenCV/MediaCodec
 * descartam P-frames órfãos.
 */
public final class H264NalUtil {

  public static final int NAL_NON_IDR = 1;
  public static final int NAL_IDR = 5;
  public static final int NAL_SPS = 7;
  public static final int NAL_PPS = 8;

  private H264NalUtil() {}

  public static boolean containsIdr(byte[] data, int len) {
    return containsType(data, len, NAL_IDR);
  }

  public static boolean containsSpsOrPps(byte[] data, int len) {
    return containsType(data, len, NAL_SPS) || containsType(data, len, NAL_PPS);
  }

  public static boolean containsType(byte[] data, int len, int nalType) {
    if (data == null || len < 4) {
      return false;
    }
    int limit = Math.min(len, data.length);
    int i = 0;
    while (i < limit - 3) {
      int nalIndex = indexAfterStartCode(data, i, limit);
      if (nalIndex < 0) {
        return false;
      }
      int type = data[nalIndex] & 0x1F;
      if (type == nalType) {
        return true;
      }
      i = nalIndex + 1;
    }
    return false;
  }

  static int indexAfterStartCode(byte[] data, int from, int limit) {
    for (int i = from; i < limit - 3; i++) {
      if (data[i] != 0 || data[i + 1] != 0) {
        continue;
      }
      if (data[i + 2] == 1) {
        return i + 3;
      }
      if (i + 4 <= limit && data[i + 2] == 0 && data[i + 3] == 1) {
        return i + 4;
      }
    }
    return -1;
  }
}
