package com.felipe.elftemplate.tracking3d;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import com.felipe.elftemplate.tracking.CameraController;
import com.felipe.elftemplate.tracking.RgbPreviewBridge;
import com.felipe.elftemplate.tracking.TrackingDiagnostics;
import com.felipe.elftemplate.tracking.TrackingProvider;
import com.felipe.elftemplate.tracking.TrackingResult;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Porta de rastreamento sobre o motor métrico 3D ({@link Astra3dTrackingEngine}).
 *
 * <p>Existe para que o Modo Espelho e os jogos usem o pipeline geodésico métrico sem que nenhum
 * consumidor mude: entrega o mesmo {@link TrackingResult} e a mesma silhueta de depuração que o
 * provider antigo baseado em blob.
 *
 * <p>Mora em {@code tracking3d}, e não em {@code tracking}, porque o motor métrico já depende de
 * {@code tracking} (reaproveita o {@code AstraDepthController} e escreve no contrato antigo).
 * Implementar a porta do outro lado fecharia um ciclo entre os dois pacotes.
 *
 * <p><b>Por que a profundidade decide sozinha.</b> O provider antigo fundia o esqueleto do depth com
 * o MoveNet da câmera RGB. Isso trazia três problemas em sala real: a inferência RGB depende de luz e
 * cai quando a sala escurece, roda a uma fração da taxa do depth e chega atrasada, e os dois
 * esqueletos discordam no punho — o que aparece como tremor. O motor métrico não precisa da RGB para
 * achar braços, então aqui a câmera HD serve apenas de plano de fundo do espelho. Menos CPU no RK3288
 * e nenhuma dependência de iluminação.
 */
public final class Astra3dTrackingProvider implements TrackingProvider {

  private static final String TAG = "Astra3dProvider";

  private final Astra3dTrackingEngine engine = new Astra3dTrackingEngine();
  private final RgbPreviewBridge previewBridge = new RgbPreviewBridge();

  private HDCameraManager hdCamera;
  private volatile CameraController cameraController;
  private ExecutorService startupExecutor;
  private volatile FrameCallback frameCallback;
  private volatile boolean bodyOnlyMask;
  private volatile boolean stopped;

  private Bitmap maskBitmap;
  private int[] maskPixels;
  private int maskWidth;
  private int maskHeight;

  /** Câmera HD do Sanbot usada como fundo; opcional, o tracking funciona sem ela. */
  public void setHdCamera(HDCameraManager manager) {
    this.hdCamera = manager;
  }

  @Override
  public Capability getCapability() {
    return Capability.LOCAL_KINECT;
  }

  @Override
  public void setRgbPreviewListener(RgbPreviewBridge.Listener listener) {
    previewBridge.setListener(listener);
  }

  /**
   * No palco fullscreen a silhueta fica sobre a imagem da câmera, então os pontos de cena saem e só
   * o corpo segmentado é desenhado.
   */
  @Override
  public void setDepthOverlayTransparent(boolean transparent) {
    bodyOnlyMask = transparent;
  }

  /**
   * Sobe tracking e preview sem bloquear quem chamou.
   *
   * <p>Abrir o stream H.264 da câmera HD envolve MediaCodec e o socket nativo do SDK, e isso somado à
   * abertura do Astra era o que travava a tela ao entrar no Modo Espelho. As Activities chamam este
   * método de {@code onMainServiceConnected}, ou seja da thread de UI, então a espera tem de sair
   * daqui.
   */
  @Override
  public void start(
      Context context, FrameCallback callback, CameraController.StreamProfile cameraProfile) {
    stopped = false;
    frameCallback = callback;
    ExecutorService worker = Executors.newSingleThreadExecutor();
    startupExecutor = worker;
    final CameraController.StreamProfile profile = cameraProfile;
    worker.execute(
        new Runnable() {
          @Override
          public void run() {
            startRgbPreview(profile);
          }
        });
    engine.start(context, this::onEngineUpdate);
  }

  private void startRgbPreview(CameraController.StreamProfile cameraProfile) {
    if (hdCamera == null) {
      Log.i(TAG, "Sem HDCameraManager: tracking métrico segue só com o Astra.");
      return;
    }
    CameraController.StreamProfile profile =
        cameraProfile != null ? cameraProfile : CameraController.StreamProfile.POSE_EFFICIENT;
    CameraController controller = new CameraController(hdCamera, profile, this::onCameraFrame);
    cameraController = controller;
    if (stopped) {
      cameraController = null;
      return;
    }
    controller.start();
    if (stopped) {
      cameraController = null;
      controller.stop();
    }
  }

  /** Chamado na thread do decoder H.264; o bridge copia para buffer duplo e entrega na hora. */
  private void onCameraFrame(byte[] nv21, int width, int height) {
    if (stopped || width <= 0 || height <= 0) {
      return;
    }
    previewBridge.offer(nv21, width, height, (width * height * 3) / 2);
  }

  /** Chamado na thread de depth do motor métrico; nada de UI aqui. */
  private void onEngineUpdate(TrackingResult result, Astra3dTelemetry telemetry) {
    if (stopped || result == null) {
      return;
    }
    applyTelemetry(result, telemetry);
    FrameCallback callback = frameCallback;
    if (callback == null) {
      return;
    }
    callback.onTrackingFrame(result, refreshMask());
  }

  /**
   * Copia a telemetria física para o bloco de diagnóstico do contrato antigo.
   *
   * <p>O adaptador já escreveu as juntas e a confiança dos braços; aqui entram só os números do
   * frame, que o motor conhece e o esqueleto não.
   */
  private void applyTelemetry(TrackingResult result, Astra3dTelemetry telemetry) {
    if (telemetry == null) {
      return;
    }
    TrackingDiagnostics diagnostics = result.diagnostics;
    diagnostics.processingLatencyMs = (long) telemetry.latencyMs;
    diagnostics.cameraFps = telemetry.fps;
    diagnostics.trackedPersonCount = telemetry.bodyCount;
    diagnostics.validPixelCount = telemetry.validPoints;
    diagnostics.frameWidth = engine.getCloud().getSourceWidth();
    diagnostics.frameHeight = engine.getCloud().getSourceHeight();
    diagnostics.peakDepthZ = Math.round(telemetry.torsoDepthM * 1000f);
  }

  /** Exporta a máscara de segmentação do frame corrente para o bitmap reaproveitado. */
  private Bitmap refreshMask() {
    if (!ensureMaskBuffers()) {
      return maskBitmap;
    }
    BodyMaskExporter.export(engine.getCloud(), engine.getSegmenter(), maskPixels, bodyOnlyMask);
    maskBitmap.setPixels(maskPixels, 0, maskWidth, 0, 0, maskWidth, maskHeight);
    return maskBitmap;
  }

  /**
   * Aloca os buffers da máscara na primeira vez e só refaz se a grade mudar de tamanho.
   *
   * @return true quando existe bitmap utilizável; em teste unitário o framework não cria bitmap.
   */
  private boolean ensureMaskBuffers() {
    int width = engine.getCloud().getGridWidth();
    int height = engine.getCloud().getGridHeight();
    if (width <= 0 || height <= 0) {
      return false;
    }
    if (maskPixels != null && maskWidth == width && maskHeight == height) {
      return maskBitmap != null;
    }
    maskWidth = width;
    maskHeight = height;
    maskPixels = new int[width * height];
    try {
      maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    } catch (Throwable t) {
      maskBitmap = null;
      Log.w(TAG, "Sem bitmap de máscara: " + t.getMessage());
    }
    return maskBitmap != null;
  }

  @Override
  public void stop() {
    stopped = true;
    frameCallback = null;
    engine.stop();
    ExecutorService worker = startupExecutor;
    startupExecutor = null;
    if (worker != null) {
      worker.shutdown();
    }
    CameraController camera = cameraController;
    cameraController = null;
    if (camera != null) {
      camera.stop();
    }
    previewBridge.clear();
  }

  /** Telemetria física do último frame: altura e pitch do sensor, estatura, distância, latência. */
  public Astra3dTelemetry getTelemetry() {
    return engine.getTelemetry();
  }

  public boolean isRunning() {
    return engine.isRunning();
  }
}
