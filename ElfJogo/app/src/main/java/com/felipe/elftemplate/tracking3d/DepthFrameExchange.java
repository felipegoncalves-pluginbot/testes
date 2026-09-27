package com.felipe.elftemplate.tracking3d;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Troca de frames de profundidade entre a thread do OpenNI e o worker, sem cópia rasgada.
 *
 * <p>Buffer triplo: um frame é do produtor ({@code back}), um é do consumidor ({@code front}) e o
 * terceiro fica no meio, trocado por {@code getAndSet} atômico. Cada frame tem sempre um único
 * dono, então o produtor nunca escreve no array que o worker está lendo.
 *
 * <p>O motor usava dois slots em rodízio. Quando o processamento passava de um período de frame (33
 * ms, comum no RK3288), o terceiro frame caía no slot que o worker ainda lia: meia nuvem de um
 * instante, meia de outro, e o esqueleto pulava. Como antes, frames intermediários são descartados:
 * em jogo só interessa o mais recente.
 */
final class DepthFrameExchange {

  /** Frame reaproveitado; o array só é realocado se a resolução crescer. */
  static final class Frame {
    short[] data;
    int width;
    int height;

    /** Volátil porque {@link #hasUnread()} lê o frame do meio sem trocá-lo. */
    volatile long sequence;
  }

  private final AtomicReference<Frame> middle = new AtomicReference<Frame>(new Frame());
  private Frame back = new Frame();
  private Frame front = new Frame();
  private long publishedSequence;
  private long consumedSequence;

  /** Produtor (thread do OpenNI): copia o frame e o publica como o mais recente. */
  void publish(short[] depthData, int width, int height) {
    Frame target = back;
    if (target.data == null || target.data.length < depthData.length) {
      target.data = new short[depthData.length];
    }
    System.arraycopy(depthData, 0, target.data, 0, depthData.length);
    target.width = width;
    target.height = height;
    target.sequence = ++publishedSequence;
    back = middle.getAndSet(target);
  }

  /**
   * Consumidor (worker): devolve o frame mais recente ainda não lido, ou null se não houver.
   *
   * <p>O frame devolvido pertence ao consumidor até a próxima chamada de {@code take()}.
   */
  Frame take() {
    front = middle.getAndSet(front);
    if (front.sequence <= consumedSequence) {
      return null;
    }
    consumedSequence = front.sequence;
    return front;
  }

  /** Consumidor: há frame publicado que ainda não foi lido. */
  boolean hasUnread() {
    return middle.get().sequence > consumedSequence;
  }
}
