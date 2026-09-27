package com.felipe.elftemplate.tracking3d;

import android.content.Context;
import com.felipe.elftemplate.tracking.CameraController;
import com.felipe.elftemplate.tracking.RgbPreviewBridge;
import com.felipe.elftemplate.tracking.TrackingProvider;
import com.sanbot.opensdk.function.unit.HDCameraManager;

/**
 * Ponto único de entrada do rastreamento corporal para todas as telas.
 *
 * <p>Existe porque o projeto chegou a ter três caminhos paralelos fazendo a mesma coisa: o Modo
 * Espelho no motor métrico, a tela de diagnóstico falando com o motor direto, e seis jogos ainda no
 * motor de blob antigo com MoveNet por cima. Cada um abria o Astra do seu jeito, e cada correção
 * precisava ser feita três vezes — ou, na prática, era feita em um lugar só e os outros dois
 * continuavam quebrados.
 *
 * <p>Aqui a escolha do motor é nomeada <b>uma vez</b>. Quem consome pede uma sessão, recebe {@link
 * com.felipe.elftemplate.tracking.TrackingResult} no contrato de sempre e não sabe qual pipeline está
 * atrás.
 *
 * <p>Também absorve duas repetições que estavam copiadas em toda Activity: fechar os streams velhos da
 * câmera HD antes de abrir o Astra (obrigatório, os dois competem pelo mesmo barramento) e escolher o
 * perfil de stream. Perfil padrão é {@code POSE_EFFICIENT}, o SUB_STREAM 640x480, que tem menos
 * latência de decode que o MAIN 720p.
 */
public final class BodyTrackingSession {

  private final Astra3dTrackingProvider provider = new Astra3dTrackingProvider();
  private CameraController.StreamProfile profile = CameraController.StreamProfile.POSE_EFFICIENT;

  /** Sessão local baseada no Astra: profundidade decide a pose, câmera HD é só imagem de fundo. */
  public static BodyTrackingSession createLocal() {
    return new BodyTrackingSession();
  }

  /**
   * Conecta a câmera HD, fechando antes qualquer stream que tenha sobrado de outra tela.
   *
   * <p>O {@code closeStream} não é zelo: sem ele o stream anterior continua aberto, o decoder novo
   * recebe fluxo sem IDR e a preview fica preta. É opcional passar {@code null}: o tracking funciona
   * apenas com o Astra, e nesse caso não há imagem de fundo.
   */
  public void attachHdCamera(HDCameraManager hdCamera) {
    if (hdCamera != null) {
      hdCamera.closeStream(0);
      hdCamera.closeStream(1);
    }
    provider.setHdCamera(hdCamera);
  }

  public void setStreamProfile(CameraController.StreamProfile streamProfile) {
    if (streamProfile != null) {
      this.profile = streamProfile;
    }
  }

  public void setRgbPreviewListener(RgbPreviewBridge.Listener listener) {
    provider.setRgbPreviewListener(listener);
  }

  /** Silhueta cobrindo só o corpo, para desenhar sobre a imagem da câmera em tela cheia. */
  public void setBodyOnlySilhouette(boolean bodyOnly) {
    provider.setDepthOverlayTransparent(bodyOnly);
  }

  /** Sobe sensor e preview. Retorna imediatamente; a abertura acontece fora da thread de UI. */
  public void start(Context context, TrackingProvider.FrameCallback callback) {
    provider.start(context, callback, profile);
  }

  public void stop() {
    provider.stop();
  }

  public boolean isRunning() {
    return provider.isRunning();
  }

  /** Telemetria física do último quadro: altura e pitch do sensor, estatura, distância, latência. */
  public Astra3dTelemetry getTelemetry() {
    return provider.getTelemetry();
  }

  /** Porta de rastreamento, para quem precisa do contrato genérico em vez da fachada. */
  public TrackingProvider getProvider() {
    return provider;
  }
}
