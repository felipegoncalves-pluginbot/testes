package com.felipe.elftemplate.tracking3d;

/**
 * Telemetria do motor métrico, em unidades físicas.
 *
 * <p>Existe para depurar no robô sem cabo: a tela mostra altura da câmera, pitch, estatura medida,
 * distância, contagem de pontos e latência. Se algo estiver errado no Sanbot, esses números dizem
 * qual etapa falhou — plano do chão, segmentação ou esqueleto — em vez de deixar só "não detectou".
 */
public final class Astra3dTelemetry {

  /** Pontos válidos na nuvem decimada. */
  public int validPoints;

  /** Corpos aprovados pelo gate antropométrico neste frame. */
  public int bodyCount;

  /** Candidatos reprovados e o motivo do maior deles: diz por que ninguém foi detectado. */
  public int rejectedCount;

  public String rejectReason = "ok";

  /** Pontos do corpo primário. */
  public int primaryBodyPoints;

  public float cameraHeightM;
  public float pitchDeg;
  public boolean floorMeasured;

  public float statureM;
  public float torsoDepthM;
  public boolean skeletonValid;
  public boolean feetVisible;

  /** Latência de processamento do frame, em milissegundos. */
  public float latencyMs;

  /** Média móvel da taxa de frames processados. */
  public float fps;

  public String lastError = "";

  /** Classificador de partes: modelo presente, células rotuladas e juntas que ele assumiu. */
  public boolean partModelActive;

  public int labeledCells;
  public int learnedProposals;
  public int learnedJoints;

  private long lastFrameMs;

  void update(
      DepthPointCloud cloud,
      GroundPlane plane,
      MetricBodySegmenter segmenter,
      MetricSkeleton skeleton,
      int bodies,
      float frameLatencyMs) {
    validPoints = cloud.getValidCount();
    bodyCount = bodies;
    primaryBodyPoints = bodies > 0 ? segmenter.getCluster(0).pointCount : 0;
    cameraHeightM = plane.getCameraHeightM();
    pitchDeg = plane.getPitchDeg();
    floorMeasured = plane.isMeasured();
    skeletonValid = skeleton.valid;
    statureM = skeleton.statureM;
    torsoDepthM = skeleton.torsoDepthM;
    feetVisible = skeleton.feetVisible;
    latencyMs = frameLatencyMs;
    rejectedCount = segmenter.getRejectedCount();
    rejectReason = PersonGate.describe(segmenter.getLastRejectReason());
    updateFps();
  }

  /** Números do classificador aprendido, separados do resto para o HUD poder mostrar a origem. */
  void updateLearned(LearnedSkeletonRefiner refiner) {
    if (refiner == null) {
      partModelActive = false;
      return;
    }
    partModelActive = refiner.isEnabled();
    labeledCells = refiner.getLabeledCells();
    learnedProposals = refiner.getProposalCount();
    learnedJoints = refiner.getOverriddenJoints();
  }

  private void updateFps() {
    long now = System.currentTimeMillis();
    if (lastFrameMs > 0 && now > lastFrameMs) {
      float instant = 1000f / (now - lastFrameMs);
      fps = fps <= 0f ? instant : (fps * 0.85f) + (instant * 0.15f);
    }
    lastFrameMs = now;
  }

  /** Resumo de uma linha para o HUD do robô. */
  public String toHudLine() {
    return String.format(
        "cam %.2fm  pitch %.1f%s  pts %d  corpos %d",
        cameraHeightM, pitchDeg, floorMeasured ? "" : "*", validPoints, bodyCount);
  }

  /** Segunda linha do HUD, focada na pessoa detectada. */
  public String toBodyLine() {
    if (!skeletonValid) {
      return String.format(
          "sem pessoa | candidatos reprovados %d (%s) | %.1f ms",
          rejectedCount, rejectReason, latencyMs);
    }
    return String.format(
        "estatura %.2fm  dist %.2fm  pes %s  %.1f fps  %.1f ms",
        statureM, torsoDepthM, feetVisible ? "sim" : "nao", fps, latencyMs);
  }

  /** Terceira linha do HUD: de onde vieram as juntas neste frame. */
  public String toLearnedLine() {
    if (!partModelActive) {
      return "classificador de partes: ausente (so geometria)";
    }
    return String.format(
        "partes: %d celulas  %d propostas  %d juntas aprendidas",
        labeledCells, learnedProposals, learnedJoints);
  }
}
