package com.felipe.elftemplate.tracking3d;

/**
 * Mede com o que a borda de um aglomerado faz fronteira.
 *
 * <p>Uma pessoa é um objeto inteiro na frente do que a cerca: na borda dela o vizinho é fundo mais
 * distante ou um trecho curto de chão nos pés. Dois tipos de recorte de parede passavam no gate
 * como corpos, e cada um se denuncia pela borda:
 *
 * <ul>
 *   <li><b>Fundo visto por fresta</b> (entre as pernas, entre braço e tronco): a borda encosta em
 *       superfície mais próxima, o próprio jogador.
 *   <li><b>Superfície cortada pelo alcance</b> (parede perto dos 4,2 m, fatiada pelo ruído): a
 *       borda continua a mesma superfície em células que ficaram além do alcance.
 * </ul>
 *
 * <p>Só o corte por alcance conta, não o de chão ou teto: esses dependem do plano do chão, e com o
 * plano de reserva (piso fora do quadro) as pernas de um jogador a 3,6 m caíam abaixo do corte do
 * chão e o jogador era reprovado.
 *
 * <p>No sintético, com o Astra na cabeça: pessoa com até 18% de borda oclusa (com outra pessoa na
 * frente) e até 9% de borda além do alcance; recortes de parede com 52–100% e 97–100%.
 */
final class ClusterBoundary {

  /**
   * Degrau de profundidade que separa superfícies, em metros.
   *
   * <p>Muito acima do ruído do Astra (σ ≈ 6 cm a 4 m) e abaixo de qualquer distância plausível
   * entre a pessoa e a parede atrás dela.
   */
  static final float SURFACE_STEP_M = 0.25f;

  private ClusterBoundary() {}

  /** Soma ao aglomerado as arestas de borda da célula e a classificação de cada uma. */
  static void countEdges(
      DepthPointCloud cloud,
      int[] labels,
      float[] worldZ,
      float maxDepthM,
      int cell,
      BodyCluster cluster) {
    int gridW = cloud.getGridWidth();
    int gx = cell % gridW;
    int gy = cell / gridW;
    if (gx > 0) {
      edge(cloud, labels, worldZ, maxDepthM, cell, cell - 1, cluster);
    }
    if (gx < gridW - 1) {
      edge(cloud, labels, worldZ, maxDepthM, cell, cell + 1, cluster);
    }
    if (gy > 0) {
      edge(cloud, labels, worldZ, maxDepthM, cell, cell - gridW, cluster);
    }
    if (gy < cloud.getGridHeight() - 1) {
      edge(cloud, labels, worldZ, maxDepthM, cell, cell + gridW, cluster);
    }
  }

  private static void edge(
      DepthPointCloud cloud,
      int[] labels,
      float[] worldZ,
      float maxDepthM,
      int from,
      int to,
      BodyCluster cluster) {
    if (labels[to] == labels[from] || !cloud.isValid(to)) {
      return;
    }
    cluster.boundaryEdges++;
    float step = worldZ[to] - worldZ[from];
    if (step < -SURFACE_STEP_M) {
      cluster.occludedEdges++;
    } else if (worldZ[to] > maxDepthM && step < SURFACE_STEP_M) {
      cluster.beyondRangeEdges++;
    }
  }
}
