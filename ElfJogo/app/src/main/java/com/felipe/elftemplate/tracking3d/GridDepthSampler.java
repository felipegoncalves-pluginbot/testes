package com.felipe.elftemplate.tracking3d;

/**
 * Adapta a nuvem decimada à interface de amostragem que a floresta consome.
 *
 * <p>É a peça que garante que o classificador no robô veja exatamente o mesmo tipo de dado que viu no
 * treino: profundidade em metros na grade decimada, com o mesmo valor convencional para pixel sem
 * leitura. Uma divergência de convenção aqui não daria erro nenhum, só acurácia pior — o modo de
 * falha mais caro de diagnosticar num sistema aprendido.
 *
 * <p>Reutilizável entre frames: {@link #bind} troca a nuvem sem alocar.
 */
public final class GridDepthSampler implements DepthPartFeature.DepthSampler {

  private DepthPointCloud cloud;
  private int gridWidth;
  private int gridHeight;
  private float focalPx;

  public void bind(DepthPointCloud pointCloud) {
    this.cloud = pointCloud;
    if (pointCloud == null) {
      gridWidth = 0;
      gridHeight = 0;
      focalPx = 1f;
      return;
    }
    gridWidth = pointCloud.getGridWidth();
    gridHeight = pointCloud.getGridHeight();
    focalPx = pointCloud.getIntrinsics().getFx();
  }

  public boolean isBound() {
    return cloud != null && gridWidth > 0 && gridHeight > 0;
  }

  @Override
  public int getWidth() {
    return gridWidth;
  }

  @Override
  public int getHeight() {
    return gridHeight;
  }

  @Override
  public float depthAt(int x, int y) {
    float depth = cloud.z((y * gridWidth) + x);
    return depth > 0f ? depth : DepthPartFeature.BACKGROUND_DEPTH_M;
  }

  @Override
  public float focalLengthPx() {
    return focalPx;
  }
}
