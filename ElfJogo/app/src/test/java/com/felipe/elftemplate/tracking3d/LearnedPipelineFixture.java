package com.felipe.elftemplate.tracking3d;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Roda o pipeline completo com o classificador aprendido sobre uma cena de pose conhecida.
 *
 * <p>Carrega o <b>mesmo asset que vai no APK</b>, não um modelo treinado na hora. É a diferença entre
 * testar o algoritmo e testar o que o robô vai executar: um modelo treinado dentro do teste poderia
 * passar com hiperparâmetros que nunca foram usados para gerar o artefato real.
 *
 * <p>A pose é dada por ângulos explícitos, então o teste sabe onde cada junta está de verdade e pode
 * medir erro em centímetros em vez de comparar com constantes escolhidas a dedo.
 */
final class LearnedPipelineFixture {

  final SyntheticDepthRenderer renderer = new SyntheticDepthRenderer();
  final LabeledHumanScene scene = new LabeledHumanScene(renderer);
  final PosedHumanSkeleton truth = new PosedHumanSkeleton();
  final DepthPointCloud cloud = new DepthPointCloud();
  final GroundPlane plane = new GroundPlane();
  final GroundPlaneEstimator groundEstimator = new GroundPlaneEstimator();
  final MetricBodySegmenter segmenter = new MetricBodySegmenter();
  final GeodesicSkeletonFitter fitter = new GeodesicSkeletonFitter();
  final MetricSkeleton skeleton = new MetricSkeleton();
  final BodyPartLabeler labeler = new BodyPartLabeler();
  final BodyPartJointProposer proposer = new BodyPartJointProposer();

  byte[] truthLabels = new byte[SyntheticDepthRenderer.WIDTH * SyntheticDepthRenderer.HEIGHT];
  int clusterCount;
  boolean skeletonFitted;
  int labeledCells;
  int proposalCount;

  final LearnedSkeletonRefiner refiner = new LearnedSkeletonRefiner();
  final SkeletonJointFilter jointFilter = new SkeletonJointFilter();

  /** Carrega o modelo embarcado no rotulador e no refinador. False se o asset não foi gerado. */
  boolean loadShippedModel() throws IOException {
    Path assetPath = locateAsset();
    if (assetPath == null || !Files.exists(assetPath)) {
      return false;
    }
    return loadInto(assetPath, labeler) && loadInto(assetPath, refiner);
  }

  private static boolean loadInto(Path assetPath, BodyPartLabeler target) throws IOException {
    InputStream input = Files.newInputStream(assetPath);
    try {
      return target.load(input);
    } finally {
      input.close();
    }
  }

  private static boolean loadInto(Path assetPath, LearnedSkeletonRefiner target)
      throws IOException {
    InputStream input = Files.newInputStream(assetPath);
    try {
      return target.load(input);
    } finally {
      input.close();
    }
  }

  private static Path locateAsset() {
    Path fromModule = Paths.get("src/main/assets").resolve(BodyPartForestCodec.ASSET_NAME);
    if (Files.exists(fromModule)) {
      return fromModule;
    }
    Path fromRoot = Paths.get("app/src/main/assets").resolve(BodyPartForestCodec.ASSET_NAME);
    return Files.exists(fromRoot) ? fromRoot : null;
  }

  /** Configura a câmera na geometria real do Sanbot: Astra na cabeça, a 0,80 m, nivelado. */
  void standardCamera(float personDepthM) {
    renderer.setCamera(0.80f, 0f);
    renderer.setBackWallDepthM(Math.max(3.9f, personDepthM + 1.0f));
    renderer.setNoise(0.0030f, 0.02f);
    renderer.getTracer().clear();
  }

  /** Braços em pose simétrica dada por elevação, azimute e flexão do cotovelo. */
  void poseArms(float statureM, float elevationDeg, float azimuthDeg, float elbowFlexDeg) {
    truth.setBody(statureM, 1f, 0f, 0f);
    truth.setArm(true, radians(elevationDeg), radians(azimuthDeg), radians(elbowFlexDeg));
    truth.setArm(false, radians(elevationDeg), radians(azimuthDeg), radians(elbowFlexDeg));
    truth.setLeg(true, 0f, radians(4f), 0f);
    truth.setLeg(false, 0f, radians(4f), 0f);
  }

  /** Pose assimétrica: só o braço anatomicamente esquerdo muda de elevação. */
  void poseLeftArmOnly(float statureM, float leftElevationDeg, float rightElevationDeg) {
    truth.setBody(statureM, 1f, 0f, 0f);
    truth.setArm(true, radians(leftElevationDeg), 0f, 0f);
    truth.setArm(false, radians(rightElevationDeg), 0f, 0f);
    truth.setLeg(true, 0f, radians(4f), 0f);
    truth.setLeg(false, 0f, radians(4f), 0f);
  }

  /** Renderiza a cena montada e executa nuvem, piso, segmentação, esqueleto, rótulos e propostas. */
  void run(float personDepthM, long seed) {
    truth.build(personDepthM, 0f, 0f);
    scene.emit(truth);
    short[] depth = renderer.renderLabeled(seed, truthLabels);
    cloud.build(depth, SyntheticDepthRenderer.WIDTH, SyntheticDepthRenderer.HEIGHT);
    groundEstimator.fit(cloud, plane);
    clusterCount = segmenter.segment(cloud, plane);
    skeletonFitted =
        clusterCount > 0 && fitter.fit(cloud, segmenter, segmenter.getCluster(0), skeleton, 1000L);
    labeledCells = 0;
    proposalCount = 0;
    if (!skeletonFitted) {
      return;
    }
    labeledCells = labeler.label(cloud, segmenter, segmenter.getCluster(0));
    proposalCount =
        proposer.propose(cloud, segmenter, labeler, segmenter.getCluster(0), skeleton.statureM);
  }

  /** Erro 3D em metros entre a junta proposta pelo classificador e a verdade renderizada. */
  float proposalErrorM(int joint) {
    float dx = proposer.x(joint) - truth.x(joint);
    float dy = proposer.y(joint) - truth.y(joint);
    float dz = proposer.z(joint) - truth.z(joint);
    return (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
  }

  /** Aplica o refinador aprendido ao esqueleto já ajustado e devolve quantas juntas ele assumiu. */
  int refineSkeletonWithLearnedModel() {
    return refiner.refine(cloud, segmenter, segmenter.getCluster(0), skeleton);
  }

  /**
   * Um quadro do caminho de produção inteiro: pipeline, refinamento e filtro temporal.
   *
   * <p>É a única forma honesta de medir tremor. O proponente é um estimador por quadro, como o do
   * Kinect, e por quadro ele oscila; o que chega à tela e aos motores é a saída do filtro One-Euro. Um
   * teste que mede só o estágio cru cobra do classificador uma propriedade que não é dele.
   */
  void runProductionFrame(float personDepthM, long seed, int frameIndex) {
    standardCamera(personDepthM);
    run(personDepthM, seed);
    if (!skeletonFitted) {
      return;
    }
    refineSkeletonWithLearnedModel();
    skeleton.trackId = 1;
    skeleton.timestampMs = 1000L + (frameIndex * 33L);
    jointFilter.filter(skeleton);
  }

  /** Erro 3D em metros entre a junta final do esqueleto (já refinada) e a verdade. */
  float skeletonErrorM(int joint) {
    float dx = skeleton.x(joint) - truth.x(joint);
    float dy = skeleton.y(joint) - truth.y(joint);
    float dz = skeleton.z(joint) - truth.z(joint);
    return (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
  }

  /**
   * Mensagem com os números medidos, decompostos por eixo.
   *
   * <p>A decomposição não é enfeite: um erro concentrado em X num braço horizontal significa viés ao
   * longo do membro, e um erro concentrado em Z significa que o empurrão da casca para o centro está
   * errado. São causas diferentes, e o total sozinho não distingue.
   */
  String describe(int joint, String name) {
    return String.format(
        "%s: erro %.1f cm (dx %.1f dy %.1f dz %.1f), conf %.2f, celulas da parte %d, "
            + "rotuladas %d, propostas %d",
        name,
        proposalErrorM(joint) * 100f,
        (proposer.x(joint) - truth.x(joint)) * 100f,
        (proposer.y(joint) - truth.y(joint)) * 100f,
        (proposer.z(joint) - truth.z(joint)) * 100f,
        proposer.confidenceOf(joint),
        partCellsFor(joint),
        labeledCells,
        proposalCount);
  }

  /** Distribuição de células por parte, para ver onde o classificador colocou os pixels do braço. */
  String describePartCounts() {
    StringBuilder text = new StringBuilder("celulas por parte:");
    for (int part = 1; part < BodyPart.COUNT; part++) {
      int count = labeler.cellCountOf(part);
      if (count > 0) {
        text.append(' ').append(BodyPart.name(part)).append('=').append(count);
      }
    }
    return text.toString();
  }

  /** Quantas células o classificador atribuiu à parte que alimenta esta junta. */
  int partCellsFor(int joint) {
    for (int part = 0; part < BodyPart.COUNT; part++) {
      if (BodyPart.jointOf(part) == joint) {
        return labeler.cellCountOf(part);
      }
    }
    return 0;
  }

  private static float radians(float degrees) {
    return (float) Math.toRadians(degrees);
  }
}
