package com.felipe.elftemplate.tracking3d;

import android.content.Context;
import android.util.Log;
import com.felipe.elftemplate.tracking.AstraDepthController;
import com.felipe.elftemplate.tracking.TrackingResult;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Motor de rastreamento métrico 3D rodando sobre o stream real do Orbbec Astra.
 *
 * <p>Reaproveita {@link AstraDepthController}, que é o único pedaço do pipeline antigo que estava
 * certo: ele fala OpenNI2 de verdade, com o helper oficial de permissão USB. Todo o resto do caminho
 * — nuvem métrica, plano do chão, segmentação, esqueleto geodésico, filtro e gestos — é novo.
 *
 * <p>Contrapressão: buffer triplo ({@link DepthFrameExchange}) com descarte do frame intermediário.
 * Um frame atrasado não vale nada em jogo, então processar sempre o mais recente é melhor que
 * enfileirar.
 *
 * <p>O plano do chão é reestimado periodicamente, não a cada frame: a montagem do sensor no robô não
 * muda em milissegundos, e a busca de pitch é a etapa mais caras do pipeline.
 */
public final class Astra3dTrackingEngine {

  private static final String TAG = "Astra3D";

  /** Intervalo de reestimativa do plano do chão, em frames. */
  private static final int GROUND_REFIT_INTERVAL = 30;

  /** Frames sem pessoa antes de zerar filtro e gestos. */
  private static final int LOST_FRAMES_TO_RESET = 8;

  /** Callback de resultado, já no contrato consumido pelos jogos existentes. */
  public interface Listener {
    void onTrackingUpdate(TrackingResult result, Astra3dTelemetry telemetry);
  }

  private final DepthPointCloud cloud = new DepthPointCloud();
  private final GroundPlane plane = new GroundPlane();
  private final GroundPlaneEstimator groundEstimator = new GroundPlaneEstimator();
  private final MetricBodySegmenter segmenter = new MetricBodySegmenter();
  private final GeodesicSkeletonFitter fitter = new GeodesicSkeletonFitter();
  private final MetricSkeleton skeleton = new MetricSkeleton();
  private final LearnedSkeletonRefiner learnedRefiner = new LearnedSkeletonRefiner();
  private final SkeletonContinuity continuity = new SkeletonContinuity();
  private final SkeletonJointFilter jointFilter = new SkeletonJointFilter();
  private final MetricGestureEngine gestures = new MetricGestureEngine();
  private final TrackingResult output = new TrackingResult();
  private final Astra3dTelemetry telemetry = new Astra3dTelemetry();

  private final DepthFrameExchange frames = new DepthFrameExchange();
  private final AtomicBoolean processing = new AtomicBoolean(false);

  private AstraDepthController astra;
  private volatile ExecutorService worker;
  private volatile Listener listener;
  private volatile boolean running;
  private int frameCounter;
  private int lostFrames;

  /**
   * Abre o sensor e começa a processar. Deve ser chamado com o serviço do robô já conectado.
   *
   * <p><b>Retorna imediatamente.</b> Carregar o modelo de partes e subir OpenNI custou, medido no
   * Elf, entre 300 e 500 ms; junto com a abertura do stream isso aparecia como {@code Choreographer:
   * Skipped 132 frames} e a tela congelava por quase dois segundos ao entrar no Modo Espelho. Nada
   * disso precisa da thread de UI, então tudo acontece no worker que já existe para processar
   * profundidade.
   */
  public void start(Context context, Listener resultListener) {
    if (running) {
      return;
    }
    this.listener = resultListener;
    this.running = true;
    this.frameCounter = 0;
    this.lostFrames = 0;
    ExecutorService startupWorker = Executors.newSingleThreadExecutor();
    this.worker = startupWorker;
    final Context appContext = context;
    startupWorker.execute(
        new Runnable() {
          @Override
          public void run() {
            openSensor(appContext);
          }
        });
  }

  /**
   * Carrega o modelo e abre o Astra, já fora da thread de UI.
   *
   * <p>Confere {@code running} antes e depois de abrir: {@link #stop()} pode ter sido chamado
   * enquanto isto rodava, e um dispositivo USB aberto depois do stop ficaria pendurado até o processo
   * morrer.
   */
  private void openSensor(Context context) {
    if (!running) {
      return;
    }
    // O estado da sessão anterior é zerado aqui, no worker, e não no stop(): lá ele rodava na UI
    // enquanto o worker antigo ainda terminava um frame com os mesmos filtros. Sem zerar a saída,
    // os primeiros frames sem jogador republicavam o esqueleto da partida anterior.
    jointFilter.reset();
    continuity.reset();
    gestures.reset();
    skeleton.reset();
    output.reset();
    learnedRefiner.load(context);
    try {
      AstraDepthController controller = new AstraDepthController(context, this::enqueue);
      astra = controller;
      if (!running) {
        astra = null;
        controller.stop();
        return;
      }
      controller.start();
      if (!running) {
        astra = null;
        controller.stop();
        return;
      }
      Log.i(
          TAG,
          "[ASTRA-3D] Motor métrico iniciado. Classificador de partes: "
              + (learnedRefiner.isEnabled()
                  ? "ativo"
                  : "ausente (" + learnedRefiner.getLoadError() + ")"));
    } catch (Throwable t) {
      Log.e(TAG, "[ASTRA-3D] Falha ao abrir o Astra: " + t.getMessage(), t);
      telemetry.lastError = String.valueOf(t.getMessage());
    }
  }

  private void enqueue(short[] depthData, int width, int height) {
    if (!running || depthData == null) {
      return;
    }
    frames.publish(depthData, width, height);
    if (processing.compareAndSet(false, true)) {
      scheduleDrain();
    }
  }

  /**
   * Agenda o dreno no worker atual, tolerando um {@link #stop()} concorrente.
   *
   * <p>Roda também na thread nativa do OpenNI: uma {@code RejectedExecutionException} ou um NPE
   * aqui voltaria para dentro do callback JNI.
   */
  private void scheduleDrain() {
    ExecutorService current = worker;
    if (!running || current == null || current.isShutdown()) {
      processing.set(false);
      return;
    }
    try {
      current.execute(this::drain);
    } catch (RejectedExecutionException e) {
      processing.set(false);
    }
  }

  private void drain() {
    try {
      while (running) {
        DepthFrameExchange.Frame frame = frames.take();
        if (frame == null) {
          return;
        }
        processFrame(frame.data, frame.width, frame.height);
      }
    } catch (Throwable t) {
      Log.e(TAG, "[ASTRA-3D] Erro no processamento do frame: " + t.getMessage(), t);
      telemetry.lastError = String.valueOf(t.getMessage());
    } finally {
      processing.set(false);
      if (running && frames.hasUnread() && processing.compareAndSet(false, true)) {
        scheduleDrain();
      }
    }
  }

  /** Uma passada completa: profundidade em milímetros entra, esqueleto e gestos saem. */
  public void processFrame(short[] depthData, int width, int height) {
    long startNanos = System.nanoTime();
    cloud.build(depthData, width, height);
    if ((frameCounter % GROUND_REFIT_INTERVAL) == 0) {
      groundEstimator.fit(cloud, plane);
    }
    frameCounter++;
    int bodies = segmenter.segment(cloud, plane);
    boolean fitted = bodies > 0 && fitSkeleton();
    if (fitted) {
      lostFrames = 0;
      jointFilter.filter(skeleton);
      gestures.update(skeleton);
      TrackingResultAdapter.apply(skeleton, plane, cloud.getIntrinsics(), output);
      gestures.applyTo(output);
    } else {
      handleNoPlayer();
    }
    publish(bodies, startNanos);
  }

  /**
   * Ajusta o esqueleto e, em seguida, deixa o classificador aprendido corrigir o que ele souber.
   *
   * <p>A geometria roda primeiro porque o classificador precisa da estatura estimada para dimensionar
   * a largura de banda do mean shift. Depois o refinador substitui junta por junta onde tem convicção.
   */
  private boolean fitSkeleton() {
    BodyCluster primary = segmenter.getCluster(0);
    boolean fitted = fitter.fit(cloud, segmenter, primary, skeleton, System.currentTimeMillis());
    if (fitted) {
      skeleton.trackId = 1;
      learnedRefiner.refine(cloud, segmenter, primary, skeleton);
      // Continuidade antes do filtro: o filtro suaviza movimento, não troca de fonte de medição.
      continuity.apply(skeleton);
    }
    return fitted;
  }

  private void handleNoPlayer() {
    lostFrames++;
    if (lostFrames >= LOST_FRAMES_TO_RESET) {
      jointFilter.reset();
      continuity.reset();
      gestures.reset();
      skeleton.reset();
      output.reset();
    }
  }

  private void publish(int bodies, long startNanos) {
    telemetry.update(
        cloud, plane, segmenter, skeleton, bodies, (System.nanoTime() - startNanos) / 1000000f);
    telemetry.updateLearned(learnedRefiner);
    Listener current = listener;
    if (current != null) {
      current.onTrackingUpdate(output, telemetry);
    }
  }

  /** Fecha o sensor e as threads. Seguro de chamar mais de uma vez. */
  public void stop() {
    running = false;
    listener = null;
    final ExecutorService oldWorker = worker;
    final AstraDepthController oldAstra = astra;
    worker = null;
    astra = null;
    if (oldWorker != null) {
      oldWorker.shutdownNow();
    }
    if (oldAstra != null) {
      oldAstra.stop();
    }
  }

  public boolean isRunning() {
    return running;
  }

  /** Resultado no contrato antigo; válido apenas dentro do callback. */
  public TrackingResult getOutput() {
    return output;
  }

  public MetricSkeleton getSkeleton() {
    return skeleton;
  }

  public GroundPlane getGroundPlane() {
    return plane;
  }

  public DepthPointCloud getCloud() {
    return cloud;
  }

  /** Segmentador do último frame; usado pelo overlay de depuração para exportar a máscara. */
  public MetricBodySegmenter getSegmenter() {
    return segmenter;
  }

  public Astra3dTelemetry getTelemetry() {
    return telemetry;
  }

  /** Refinador aprendido; exposto para o overlay de depuração mostrar o mapa de partes. */
  public LearnedSkeletonRefiner getLearnedRefiner() {
    return learnedRefiner;
  }
}
