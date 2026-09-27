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
   * Cenário padrão: Astra na cabeça do Sanbot, a 0,80 m, nivelado, pessoa de frente.
   *
   * <p>Nessa geometria a cabeça de um adulto de 1,75 m entra no quadro a partir de ~2,3 m e o piso
   * aparece a partir de ~1,9 m. Por isso os testes usam 2,5 m como distância de referência.
   */
  void standardScene(float statureM, float depthM, SyntheticHumanScene.Pose pose) {
    renderer.setCamera(0.80f, 0f);
    renderer.setBackWallDepthM(Math.max(3.8f, depthM + 1.2f));
    renderer.getTracer().clear();
    scene.addPerson(statureM, depthM, 0f, pose);
  }

  BodyCluster primaryCluster() {
    return segmenter.getCluster(0);
  }
}
