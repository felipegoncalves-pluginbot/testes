package com.felipe.elftemplate.tracking;

/**
 * Slot H.264 latest-wins. Copia o NALU do SDK (buffer reutilizado) e descarta o pacote antigo,
 * nunca o mais novo.
 */
public final class LatestEncodedSlot {

  private static final int MAX_NAL_BYTES = 1_000_000;

  private final Object lock = new Object();
  private byte[] buf = new byte[64 * 1024];
  private int len;
  private boolean pending;
  private boolean dropped;

  public void offer(byte[] data) {
    if (data == null || data.length == 0 || data.length > MAX_NAL_BYTES) {
      return;
    }
    synchronized (lock) {
      if (pending) {
        dropped = true;
      }
      if (buf.length < data.length) {
        buf = new byte[data.length];
      }
      System.arraycopy(data, 0, buf, 0, data.length);
      len = data.length;
      pending = true;
    }
  }

  /**
   * Copia o pendente para {@code dest}. Retorna o tamanho, 0 se vazio, ou {@code -needed} se dest
   * for curto. {@code droppedOut[0]} fica true se um pacote mais novo substituiu outro.
   */
  public int copyTo(byte[] dest, boolean[] droppedOut) {
    if (dest == null) {
      return 0;
    }
    synchronized (lock) {
      if (!pending) {
        return 0;
      }
      if (dest.length < len) {
        return -len;
      }
      System.arraycopy(buf, 0, dest, 0, len);
      int n = len;
      pending = false;
      if (droppedOut != null && droppedOut.length > 0) {
        droppedOut[0] = dropped;
      }
      dropped = false;
      return n;
    }
  }

  public int pendingLength() {
    synchronized (lock) {
      return pending ? len : 0;
    }
  }
}
