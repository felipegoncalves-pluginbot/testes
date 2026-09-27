package com.felipe.elftemplate.tracking3d;

/**
 * Roda o pipeline métrico completo sobre um frame sintético e expõe os estágios intermediários.
 *
 * <p>Evita repetir a montagem em cada teste e deixa explícito que os testes atacam o pipeline real,
 * não uma versão simplificada dele.
 */
final class MetricPipelineFixture {

  final SyntheticDepthRenderer renderer = new SyntheticDepthRenderer();
  final SyntheticHumanScene scene = new SyntheticHumanScene(renderer);
  final DepthPointCloud cloud = new DepthPointCloud();
  final GroundPlane plane = new GroundPlane();
  final GroundPlaneEstimator groundEstimator = new GroundPlaneEstimator();
  final MetricBodySegmenter segmenter = new MetricBodySegmenter();
  final GeodesicSkeletonFitter fitter = new GeodesicSkeletonFitter();
  final MetricSkeleton skeleton = new MetricSkeleton();

  int clusterCount;
  boolean skeletonFitted;

  /** Renderiza a cena atual e executa nuvem, plano do chão, segmentação e esqueleto. */
  void run(long seed) {
    short[] depth = renderer.render(seed);
    cloud.build(depth, SyntheticDepthRenderer.WIDTH, SyntheticDepthRenderer.HEIGHT);
    groundEstimator.fit(cloud, plane);
    clusterCount = segmenter.segment(cloud, plane);
    skeletonFitted =
        clusterCount > 0 && fitter.fit(cloud, segmenter, segmenter.getCluster(0), skeleton, 1000L);
  }

  /**
   * Cenário padrão legado: sensor a 1,05 m, inclinado 5° para baixo, pessoa de frente.
   *
   * <p>Com esse pitch e essa altura, um adulto cabe inteiro no quadro a partir de ~2,2 m e o piso
   * aparece a partir de ~2,0 m. No robô o Astra fica na cabeça, a ~0,8 m: os casos dessa geometria
   * estão em {@code RobotMountingTrackingTest}.
   */
  void standardScene(float statureM, float depthM, SyntheticHumanScene.Pose pose) {
    renderer.setCamera(1.05f, 5f);
    renderer.setBackWallDepthM(Math.max(3.8f, depthM + 1.2f));
    renderer.getTracer().clear();
    scene.addPerson(statureM, depthM, 0f, pose);
  }

  BodyCluster primaryCluster() {
    return segmenter.getCluster(0);
  }
}
