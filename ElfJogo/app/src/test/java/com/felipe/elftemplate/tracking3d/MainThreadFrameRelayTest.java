package com.felipe.elftemplate.tracking3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import com.felipe.elftemplate.tracking.TrackingProvider;
import com.felipe.elftemplate.tracking.TrackingResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;

public class MainThreadFrameRelayTest {

  /** Fila da "thread de UI": nada roda até o teste mandar. */
  private static final class QueuedUi implements Executor {
    final List<Runnable> queue = new ArrayList<Runnable>();

    @Override
    public void execute(Runnable task) {
      queue.add(task);
    }

    void runAll() {
      List<Runnable> batch = new ArrayList<Runnable>(queue);
      queue.clear();
      for (Runnable task : batch) {
        task.run();
      }
    }
  }

  private static final class RecordingGame implements TrackingProvider.FrameCallback {
    final List<TrackingResult> received = new ArrayList<TrackingResult>();
    final List<Float> centroids = new ArrayList<Float>();

    @Override
    public void onTrackingFrame(TrackingResult result, Bitmap debugSilhouette) {
      received.add(result);
      centroids.add(result.playerCentroidX);
    }
  }

  private static TrackingResult frameAt(TrackingResult reused, float centroidX) {
    reused.isPlayerPresent = true;
    reused.playerCentroidX = centroidX;
    reused.diagnostics.trackedPersonCount = 1;
    return reused;
  }

  @Test
  public void framesArrivingWhileUiIsBusyBecomeOneDeliveryOfTheLatest() {
    QueuedUi ui = new QueuedUi();
    RecordingGame game = new RecordingGame();
    MainThreadFrameRelay relay = new MainThreadFrameRelay(ui, game);
    TrackingResult engineOutput = new TrackingResult();

    relay.onTrackingFrame(frameAt(engineOutput, 0.2f), null);
    relay.onTrackingFrame(frameAt(engineOutput, 0.3f), null);
    relay.onTrackingFrame(frameAt(engineOutput, 0.4f), null);
    assertEquals("uma entrega pendente, não três", 1, ui.queue.size());

    ui.runAll();
    assertEquals(1, game.centroids.size());
    assertEquals("jogo recebe o frame mais recente", 0.4f, game.centroids.get(0), 1e-6f);
    assertEquals("diagnóstico copiado", 1, game.received.get(0).diagnostics.trackedPersonCount);
  }

  @Test
  public void gameGetsItsOwnSnapshotNotTheEngineObject() {
    QueuedUi ui = new QueuedUi();
    RecordingGame game = new RecordingGame();
    MainThreadFrameRelay relay = new MainThreadFrameRelay(ui, game);
    TrackingResult engineOutput = new TrackingResult();

    relay.onTrackingFrame(frameAt(engineOutput, 0.25f), null);
    ui.runAll();
    engineOutput.reset();

    TrackingResult delivered = game.received.get(0);
    assertNotSame("objeto do motor não vaza para a UI", engineOutput, delivered);
    assertTrue("reset do motor não apaga o que o jogo guardou", delivered.isPlayerPresent);
    assertEquals(0.25f, delivered.playerCentroidX, 1e-6f);
  }

  @Test
  public void closedRelayDropsTheDeliveryAlreadyQueued() {
    QueuedUi ui = new QueuedUi();
    RecordingGame game = new RecordingGame();
    MainThreadFrameRelay relay = new MainThreadFrameRelay(ui, game);

    relay.onTrackingFrame(frameAt(new TrackingResult(), 0.5f), null);
    relay.close();
    ui.runAll();
    relay.onTrackingFrame(frameAt(new TrackingResult(), 0.6f), null);

    assertEquals("nenhum frame depois do stop", 0, game.received.size());
    assertEquals("nada novo agendado depois do close", 0, ui.queue.size());
  }
}
