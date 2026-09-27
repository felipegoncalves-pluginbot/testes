package com.felipe.elftemplate.mirror;

import com.felipe.elftemplate.server.MirrorTrackingRelayWs;
import com.felipe.elftemplate.tracking.MirrorTrackingRelay;
import com.felipe.elftemplate.tracking3d.BodyTrackingSession;

/**
 * Monta a sessão do Modo Espelho.
 *
 * <p>O tracking vem da mesma {@link BodyTrackingSession} que todas as outras telas usam. O relay WiFi
 * continua no ar, mas só como canal de telemetria e depuração: ele não entra mais no esqueleto.
 *
 * <p><b>Por que o auxiliar remoto saiu do caminho do esqueleto.</b> Ele existia para compensar um
 * detector de braços que errava — mandava o quadro RGB para um celular rodar MoveNet e devolvia os
 * punhos. O preço era alto: dependência de luz, taxa e latência de rede, e dois esqueletos discordando
 * no punho, que é exatamente o que se enxerga como tremor. Com os membros resolvidos na profundidade, a
 * muleta virou fonte de ruído.
 */
public final class MirrorSessionSetup {

  public static final class Bundle {
    public final BodyTrackingSession tracking;
    public final MirrorTrackingRelay relay;

    Bundle(BodyTrackingSession tracking, MirrorTrackingRelay relay) {
      this.tracking = tracking;
      this.relay = relay;
    }

    public void startRelay() {
      relay.start(MirrorTrackingRelay.DEFAULT_PORT);
    }

    public void stopRelay() {
      relay.stop();
    }
  }

  private MirrorSessionSetup() {}

  /** Sessão do espelho com o motor métrico 3D e o relay de depuração ativo. */
  public static Bundle createMetric3d() {
    return new Bundle(BodyTrackingSession.createLocal(), new MirrorTrackingRelayWs());
  }
}
