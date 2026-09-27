package com.felipe.elftemplate.tracking3d;

import android.graphics.Bitmap;
import com.felipe.elftemplate.tracking.TrackingProvider;
import com.felipe.elftemplate.tracking.TrackingResult;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entrega frames de tracking na thread de UI: um retrato próprio por entrega, no máximo uma entrega
 * pendente.
 *
 * <p>Os jogos faziam {@code runOnUiThread} a cada frame passando o {@link TrackingResult} do motor,
 * que é um objeto só, reescrito no worker. Isso tinha dois efeitos. Primeiro, a UI lia o esqueleto
 * no meio da escrita do frame seguinte ({@code reset()} seguido das juntas), e ele piscava para a
 * pose padrão. Segundo, quando a UI atrasava, os runnables se acumulavam, todos lendo o mesmo
 * objeto, e o jogo ficava cada vez mais atrasado em relação ao jogador.
 *
 * <p>Aqui o worker copia o frame para {@code pending} sob trava e agenda no máximo um dreno. O
 * dreno copia para {@code delivered}, que só a thread de UI toca, e é esse objeto que o jogo recebe
 * e pode guardar para desenhar depois.
 */
final class MainThreadFrameRelay implements TrackingProvider.FrameCallback {

  private final Executor uiExecutor;
  private final TrackingProvider.FrameCallback target;
  private final Object lock = new Object();
  private final TrackingResult pending = new TrackingResult();
  private final TrackingResult delivered = new TrackingResult();
  private final AtomicBoolean drainPosted = new AtomicBoolean(false);
  private final Runnable drainTask =
      new Runnable() {
        @Override
        public void run() {
          drain();
        }
      };

  private Bitmap pendingMask;
  private volatile boolean closed;

  MainThreadFrameRelay(Executor uiExecutor, TrackingProvider.FrameCallback target) {
    this.uiExecutor = uiExecutor;
    this.target = target;
  }

  /** Thread do worker: guarda o frame mais recente e agenda um dreno se ainda não houver um. */
  @Override
  public void onTrackingFrame(TrackingResult result, Bitmap debugSilhouette) {
    if (closed || result == null) {
      return;
    }
    synchronized (lock) {
      copy(result, pending);
      pendingMask = debugSilhouette;
    }
    if (drainPosted.compareAndSet(false, true)) {
      uiExecutor.execute(drainTask);
    }
  }

  /** Thread de UI: entrega o retrato mais recente ao jogo. */
  private void drain() {
    drainPosted.set(false);
    if (closed) {
      return;
    }
    Bitmap mask;
    synchronized (lock) {
      copy(pending, delivered);
      mask = pendingMask;
    }
    target.onTrackingFrame(delivered, mask);
  }

  /** A partir daqui nenhum frame chega ao jogo, nem o que já estava agendado. */
  void close() {
    closed = true;
  }

  private static void copy(TrackingResult src, TrackingResult dst) {
    dst.applyFrom(src);
    dst.diagnostics.copyFrom(src.diagnostics);
  }
}
