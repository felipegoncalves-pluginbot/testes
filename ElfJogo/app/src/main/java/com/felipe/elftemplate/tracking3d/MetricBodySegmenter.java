package com.felipe.elftemplate.tracking3d;

import java.util.Arrays;

/**
 * Segmenta corpos por conectividade métrica 3D, com o piso e o teto removidos geometricamente.
 *
 * <p>Duas mudanças de fundo em relação ao {@code DepthBlobClusterer} antigo:
 *
 * <ul>
 *   <li>A conectividade é distância 3D real em metros (tipicamente 3 a 7 cm, dependente da distância
 *       e do ruído do sensor), não uma banda fixa de ±260/360 mm em Z. Aquela banda larga colava o
 *       jogador na parede atrás dele e nos móveis ao lado.
 *   <li>Chão e teto saem por altura no referencial do mundo, não por "linhas abaixo de 90% do
 *       quadro". Com a câmera inclinada, o corte por linha comia os pés e deixava o piso entrar.
 * </ul>
 *
 * <p>Todos os buffers são pré-alocados e reutilizados; o hot path não aloca.
 */
public final class MetricBodySegmenter {

  /** Máximo de corpos analisados em detalhe por frame. */
  public static final int MAX_CLUSTERS = 6;

  /** Altura mínima acima do piso para um ponto entrar na segmentação (deixa os pés passarem). */
  private static final float FLOOR_CLEARANCE_M = 0.08f;

  /** Acima disso é teto, lâmpada ou viga: nunca é corpo. */
  private static final float CEILING_LIMIT_M = 2.40f;

  private static final float MIN_TRACK_DEPTH_M = 0.45f;
  private static final float MAX_TRACK_DEPTH_M = 4.20f;

  /**
   * Limites da tolerância de conectividade, em metros.
   *
   * <p>O piso precisa cobrir dobras de roupa e a transição ombro-braço, mas ficar bem abaixo da
   * distância típica entre a pessoa e a parede atrás dela.
   */
  private static final float MIN_CONNECT_TOLERANCE_M = 0.045f;

  private static final float MAX_CONNECT_TOLERANCE_M = 0.120f;

  /** Múltiplo do espaçamento da grade aceito como aresta; abaixo de 3 a grade se fragmenta. */
  private static final float CELL_SPAN_FACTOR = 3.0f;

  /** Coeficiente de ruído axial do Astra: sigma ~ 0,0035 * Z^2 (m). */
  private static final float DEPTH_NOISE_COEFF = 0.0035f;

  private final BodyCluster[] pool = new BodyCluster[MAX_CLUSTERS];

  private float[] worldY = new float[0];
  private float[] worldZ = new float[0];
  private boolean[] candidate = new boolean[0];
  private int[] labels = new int[0];
  private int[] stack = new int[0];
  private int[] labelCounts = new int[0];
  private int[] labelToSlot = new int[0];

  /** Somatórias por slot: {X, Y, Z, contagem}. Em double para não perder precisão em 20k pontos. */
  private final double[] accumulator = new double[MAX_CLUSTERS * 4];

  private int clusterCount;
  private int labelCount;
  private int rejectedCount;
  private int lastRejectReason;

  public MetricBodySegmenter() {
    for (int i = 0; i < MAX_CLUSTERS; i++) {
      pool[i] = new BodyCluster();
    }
  }

  /**
   * Executa a segmentação completa do frame.
   *
   * @return quantidade de corpos aprovados pelo {@link PersonGate}.
   */
  public int segment(DepthPointCloud cloud, GroundPlane plane) {
    clusterCount = 0;
    labelCount = 0;
    if (cloud == null || plane == null || cloud.getValidCount() <= 0) {
      return 0;
    }
    ensureCapacity(cloud.getCellCount());
    buildWorldMask(cloud, plane);
    labelConnectedComponents(cloud);
    if (labelCount <= 0) {
      return 0;
    }
    selectDominantLabels(cloud);
    if (clusterCount <= 0) {
      return 0;
    }
    accumulateClusterStats(cloud);
    return finalizeClusters(cloud, plane.isMeasured());
  }

  private void ensureCapacity(int cells) {
    if (labels.length >= cells) {
      return;
    }
    worldY = new float[cells];
    worldZ = new float[cells];
    candidate = new boolean[cells];
    labels = new int[cells];
    stack = new int[cells];
    labelCounts = new int[cells + 1];
    labelToSlot = new int[cells + 1];
  }

  /** Converte para o referencial do chão e marca as células que podem pertencer a um corpo. */
  private void buildWorldMask(DepthPointCloud cloud, GroundPlane plane) {
    int cells = cloud.getCellCount();
    Arrays.fill(candidate, 0, cells, false);
    Arrays.fill(labels, 0, cells, 0);
    for (int i = 0; i < cells; i++) {
      if (!cloud.isValid(i)) {
        continue;
      }
      float camY = cloud.y(i);
      float camZ = cloud.z(i);
      float height = plane.heightAboveFloor(camY, camZ);
      float depth = plane.horizontalDepth(camY, camZ);
      worldY[i] = height;
      worldZ[i] = depth;
      candidate[i] =
          height > FLOOR_CLEARANCE_M
              && height < CEILING_LIMIT_M
              && depth >= MIN_TRACK_DEPTH_M
              && depth <= MAX_TRACK_DEPTH_M;
    }
  }

  /** Tolerância de conectividade dependente da distância: ruído do sensor + espaçamento da grade. */
  private float connectTolerance(DepthPointCloud cloud, float depthMeters) {
    float cellSpan = cloud.cellSizeMetersAt(depthMeters) * CELL_SPAN_FACTOR;
    float noise = DEPTH_NOISE_COEFF * depthMeters * depthMeters;
    float tolerance = Math.max(cellSpan, noise);
    return Math.min(MAX_CONNECT_TOLERANCE_M, Math.max(MIN_CONNECT_TOLERANCE_M, tolerance));
  }

  private void labelConnectedComponents(DepthPointCloud cloud) {
    int cells = cloud.getCellCount();
    Arrays.fill(labelCounts, 0, cells + 1, 0);
    int nextLabel = 1;
    for (int seed = 0; seed < cells; seed++) {
      if (!candidate[seed] || labels[seed] != 0) {
        continue;
      }
      int count = floodFill(cloud, seed, nextLabel);
      labelCounts[nextLabel] = count;
      nextLabel++;
    }
    labelCount = nextLabel - 1;
  }

  /** Flood fill iterativo em 4-vizinhança, com aresta aceita por distância 3D métrica. */
  private int floodFill(DepthPointCloud cloud, int seed, int label) {
    int gridW = cloud.getGridWidth();
    int gridH = cloud.getGridHeight();
    int sp = 0;
    stack[sp++] = seed;
    labels[seed] = label;
    int count = 0;
    while (sp > 0) {
      int current = stack[--sp];
      count++;
      float tolerance = connectTolerance(cloud, worldZ[current]);
      int gx = current % gridW;
      int gy = current / gridW;
      if (gx > 0) {
        sp = tryPush(cloud, current, current - 1, label, tolerance, sp);
      }
      if (gx < gridW - 1) {
        sp = tryPush(cloud, current, current + 1, label, tolerance, sp);
      }
      if (gy > 0) {
        sp = tryPush(cloud, current, current - gridW, label, tolerance, sp);
      }
      if (gy < gridH - 1) {
        sp = tryPush(cloud, current, current + gridW, label, tolerance, sp);
      }
    }
    return count;
  }

  private int tryPush(
      DepthPointCloud cloud, int from, int to, int label, float tolerance, int stackPointer) {
    if (!candidate[to] || labels[to] != 0) {
      return stackPointer;
    }
    if (cloud.squaredDistance(from, to) > (tolerance * tolerance)) {
      return stackPointer;
    }
    labels[to] = label;
    stack[stackPointer] = to;
    return stackPointer + 1;
  }

  /** Escolhe os maiores componentes e mapeia cada rótulo para um slot do pool. */
  private void selectDominantLabels(DepthPointCloud cloud) {
    Arrays.fill(labelToSlot, 0, labelCount + 1, -1);
    int minPoints = PersonGate.minPointsAt(cloud.getMeanDepthMeters(), cloud) / 2;
    clusterCount = 0;
    for (int label = 1; label <= labelCount; label++) {
      if (labelCounts[label] < minPoints) {
        continue;
      }
      if (clusterCount < MAX_CLUSTERS) {
        assignSlot(label, clusterCount++);
        continue;
      }
      int weakest = findWeakestSlot();
      if (labelCounts[label] > labelCounts[pool[weakest].label]) {
        labelToSlot[pool[weakest].label] = -1;
        assignSlot(label, weakest);
      }
    }
  }

  private void assignSlot(int label, int slot) {
    pool[slot].reset();
    pool[slot].label = label;
    labelToSlot[label] = slot;
  }

  private int findWeakestSlot() {
    int weakest = 0;
    for (int slot = 1; slot < clusterCount; slot++) {
      if (labelCounts[pool[slot].label] < labelCounts[pool[weakest].label]) {
        weakest = slot;
      }
    }
    return weakest;
  }

  private void accumulateClusterStats(DepthPointCloud cloud) {
    int gridW = cloud.getGridWidth();
    int gridH = cloud.getGridHeight();
    for (int i = 0; i < cloud.getCellCount(); i++) {
      int label = labels[i];
      if (label == 0) {
        continue;
      }
      int slot = labelToSlot[label];
      if (slot < 0) {
        continue;
      }
      BodyCluster cluster = pool[slot];
      float wx = cloud.x(i);
      float wy = worldY[i];
      float wz = worldZ[i];
      expandBounds(cluster, wx, wy, wz);
      cluster.addBandSample(wy, wx);
      markFrameContact(cluster, i % gridW, i / gridW, gridW, gridH);
      ClusterBoundary.countEdges(cloud, labels, worldZ, MAX_TRACK_DEPTH_M, i, cluster);
      accumulator[slot * 4] += wx;
      accumulator[(slot * 4) + 1] += wy;
      accumulator[(slot * 4) + 2] += wz;
      accumulator[(slot * 4) + 3] += 1.0;
    }
  }

  private static void expandBounds(BodyCluster cluster, float wx, float wy, float wz) {
    if (wx < cluster.minWorldX) {
      cluster.minWorldX = wx;
    }
    if (wx > cluster.maxWorldX) {
      cluster.maxWorldX = wx;
    }
    if (wy < cluster.minWorldY) {
      cluster.minWorldY = wy;
    }
    if (wy > cluster.maxWorldY) {
      cluster.maxWorldY = wy;
    }
    if (wz < cluster.minWorldZ) {
      cluster.minWorldZ = wz;
    }
    if (wz > cluster.maxWorldZ) {
      cluster.maxWorldZ = wz;
    }
  }

  private static void markFrameContact(
      BodyCluster cluster, int gx, int gy, int gridW, int gridH) {
    if (gx < cluster.minGridX) {
      cluster.minGridX = gx;
    }
    if (gx > cluster.maxGridX) {
      cluster.maxGridX = gx;
    }
    if (gy < cluster.minGridY) {
      cluster.minGridY = gy;
    }
    if (gy > cluster.maxGridY) {
      cluster.maxGridY = gy;
    }
    if (gy <= 0) {
      cluster.touchesFrameTop = true;
    }
    if (gy >= gridH - 1) {
      cluster.touchesFrameBottom = true;
    }
    if (gx <= 0) {
      cluster.touchesFrameLeft = true;
    }
    if (gx >= gridW - 1) {
      cluster.touchesFrameRight = true;
    }
  }

  /** Fecha as estatísticas e compacta o pool mantendo só os corpos aprovados. */
  private int finalizeClusters(DepthPointCloud cloud, boolean floorMeasured) {
    int accepted = 0;
    rejectedCount = 0;
    lastRejectReason = PersonGate.ACCEPTED;
    int largestRejectedPoints = 0;
    for (int slot = 0; slot < clusterCount; slot++) {
      BodyCluster cluster = pool[slot];
      int base = slot * 4;
      int count = (int) accumulator[base + 3];
      cluster.finish(count, accumulator[base], accumulator[base + 1], accumulator[base + 2]);
      accumulator[base] = 0;
      accumulator[base + 1] = 0;
      accumulator[base + 2] = 0;
      accumulator[base + 3] = 0;
      int reason = PersonGate.evaluate(cluster, cloud, floorMeasured);
      if (reason != PersonGate.ACCEPTED) {
        rejectedCount++;
        // Guarda o motivo do maior candidato reprovado: é o mais provável de ser a pessoa perdida.
        if (cluster.pointCount > largestRejectedPoints) {
          largestRejectedPoints = cluster.pointCount;
          lastRejectReason = reason;
        }
        continue;
      }
      if (accepted != slot) {
        swapSlots(slot, accepted);
      }
      accepted++;
    }
    clusterCount = accepted;
    // Slot 0 = corpo mais próximo ("Closest1Player" do Kinect), não o primeiro rótulo da varredura:
    // um recorte da parede do fundo começa mais alto no quadro e roubava o lugar do jogador.
    int closest = 0;
    for (int slot = 1; slot < accepted; slot++) {
      closest = pool[slot].centroidZ < pool[closest].centroidZ ? slot : closest;
    }
    if (closest != 0) {
      swapSlots(closest, 0);
    }
    return accepted;
  }

  private void swapSlots(int from, int to) {
    BodyCluster temp = pool[to];
    pool[to] = pool[from];
    pool[from] = temp;
    labelToSlot[pool[to].label] = to;
    labelToSlot[pool[from].label] = from;
  }

  public int getClusterCount() {
    return clusterCount;
  }

  /** Aglomerados que chegaram ao gate e foram reprovados neste frame. */
  public int getRejectedCount() {
    return rejectedCount;
  }

  /** Motivo da reprovação do maior candidato rejeitado; ver constantes de {@link PersonGate}. */
  public int getLastRejectReason() {
    return lastRejectReason;
  }

  /** Corpo aprovado no slot informado; o slot 0 é sempre o mais próximo (o jogador). */
  public BodyCluster getCluster(int index) {
    return pool[index];
  }

  /** Rótulo atribuído a cada célula da grade; 0 significa fundo. */
  public int labelAt(int cellIndex) {
    return labels[cellIndex];
  }

  /** Altura acima do piso já calculada para a célula. */
  public float worldYAt(int cellIndex) {
    return worldY[cellIndex];
  }

  /** Distância horizontal já calculada para a célula. */
  public float worldZAt(int cellIndex) {
    return worldZ[cellIndex];
  }
}
