package com.felipe.elftemplate.tracking3d;

/**
 * Filtro One-Euro por junta, em metros e segundos.
 *
 * <p>Substitui a pilha EMA + Holt do pipeline antigo por um filtro só. Dois ganhos concretos:
 *
 * <ul>
 *   <li><b>Independência de FPS.</b> Os parâmetros são frequências de corte em Hz e o passo de tempo
 *       real entra na conta. O filtro antigo limitava o deslocamento "por frame", então a resposta
 *       mudava quando o processamento variava de 30 para 18 fps.
 *   <li><b>Corte adaptativo.</b> Parado, o corte cai e o tremor desaparece; em movimento rápido, o
 *       corte sobe e o atraso quase desaparece. Empilhar dois passa-baixas fixos, como antes,
 *       obrigava a escolher entre tremer ou atrasar.
 * </ul>
 */
public final class SkeletonJointFilter {

  /** Corte mínimo, em Hz: domina quando a junta está praticamente parada. */
  private static final float MIN_CUTOFF_HZ = 1.2f;

  /** Ganho sobre a velocidade estimada: quanto o corte sobe por m/s de movimento. */
  private static final float SPEED_COEFFICIENT = 1.4f;

  /** Corte do passa-baixas aplicado à própria estimativa de velocidade, em Hz. */
  private static final float DERIVATIVE_CUTOFF_HZ = 1.0f;

  /** Salto acima do qual a junta é reinicializada em vez de interpolada, em metros. */
  private static final float TELEPORT_THRESHOLD_M = 0.45f;

  private final float[] filtered = new float[MetricSkeleton.JOINT_COUNT * 3];
  private final float[] velocity = new float[MetricSkeleton.JOINT_COUNT * 3];
  private boolean initialized;
  private int activeTrackId = -1;
  private long lastTimestampMs;

  /** Zera o estado; usado quando o jogador sai de cena ou troca de identidade. */
  public void reset() {
    initialized = false;
    activeTrackId = -1;
    lastTimestampMs = 0;
  }

  /**
   * Filtra o esqueleto no lugar.
   *
   * <p>Juntas sintetizadas (confiança baixa) também são filtradas, para que o esqueleto não pisque
   * quando um membro entra e sai de detecção.
   */
  public void filter(MetricSkeleton skeleton) {
    if (skeleton == null || !skeleton.valid) {
      reset();
      return;
    }
    if (!initialized || skeleton.trackId != activeTrackId) {
      prime(skeleton);
      return;
    }
    float deltaSeconds = (skeleton.timestampMs - lastTimestampMs) / 1000f;
    if (deltaSeconds <= 0f || deltaSeconds > 0.5f) {
      prime(skeleton);
      return;
    }
    lastTimestampMs = skeleton.timestampMs;
    for (int joint = 0; joint < MetricSkeleton.JOINT_COUNT; joint++) {
      int base = joint * 3;
      if (isTeleport(skeleton, joint, base)) {
        primeJoint(skeleton, joint, base);
        continue;
      }
      float x = filterAxis(base, skeleton.x(joint), deltaSeconds);
      float y = filterAxis(base + 1, skeleton.y(joint), deltaSeconds);
      float z = filterAxis(base + 2, skeleton.z(joint), deltaSeconds);
      skeleton.set(joint, x, y, z, skeleton.confidence(joint));
    }
  }

  private boolean isTeleport(MetricSkeleton skeleton, int joint, int base) {
    float dx = skeleton.x(joint) - filtered[base];
    float dy = skeleton.y(joint) - filtered[base + 1];
    float dz = skeleton.z(joint) - filtered[base + 2];
    float squared = (dx * dx) + (dy * dy) + (dz * dz);
    return squared > (TELEPORT_THRESHOLD_M * TELEPORT_THRESHOLD_M);
  }

  private void prime(MetricSkeleton skeleton) {
    for (int joint = 0; joint < MetricSkeleton.JOINT_COUNT; joint++) {
      primeJoint(skeleton, joint, joint * 3);
    }
    initialized = true;
    activeTrackId = skeleton.trackId;
    lastTimestampMs = skeleton.timestampMs;
  }

  private void primeJoint(MetricSkeleton skeleton, int joint, int base) {
    filtered[base] = skeleton.x(joint);
    filtered[base + 1] = skeleton.y(joint);
    filtered[base + 2] = skeleton.z(joint);
    velocity[base] = 0f;
    velocity[base + 1] = 0f;
    velocity[base + 2] = 0f;
  }

  /**
   * Núcleo do One-Euro em um eixo.
   *
   * <p>Estima a velocidade, suaviza essa estimativa, deriva o corte a partir dela e aplica o
   * passa-baixas ao valor.
   */
  private float filterAxis(int slot, float rawValue, float deltaSeconds) {
    float rawSpeed = (rawValue - filtered[slot]) / deltaSeconds;
    float derivativeAlpha = smoothingAlpha(DERIVATIVE_CUTOFF_HZ, deltaSeconds);
    velocity[slot] = velocity[slot] + (derivativeAlpha * (rawSpeed - velocity[slot]));
    float cutoff = MIN_CUTOFF_HZ + (SPEED_COEFFICIENT * Math.abs(velocity[slot]));
    float alpha = smoothingAlpha(cutoff, deltaSeconds);
    filtered[slot] = filtered[slot] + (alpha * (rawValue - filtered[slot]));
    return filtered[slot];
  }

  /** Converte frequência de corte e passo de tempo no alfa do passa-baixas de primeira ordem. */
  private static float smoothingAlpha(float cutoffHz, float deltaSeconds) {
    float timeConstant = 1f / (2f * (float) Math.PI * cutoffHz);
    return 1f / (1f + (timeConstant / deltaSeconds));
  }
}
