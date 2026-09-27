package com.felipe.elftemplate.tracking3d;

import java.util.Arrays;

/**
 * Campo de distância geodésica sobre a superfície de um corpo segmentado.
 *
 * <p>Distância geodésica é a distância medida <em>ao longo do corpo</em>, não em linha reta. É a
 * medida certa para achar extremidades: uma mão colada ao quadril está a 5 cm em linha reta do
 * quadril, mas a 70 cm percorrendo ombro e braço. O pipeline antigo procurava mãos pelo pixel mais
 * lateral do blob, e por isso confundia mão com encosto de cadeira, com o batente da porta e com o
 * próprio quadril.
 *
 * <p>Implementação: Dijkstra com fila de baldes (algoritmo de Dial). Como todo peso de aresta é menor
 * que a tolerância de conectividade (≤ 120 mm), 256 baldes de 1 mm bastam e a fila fica O(1) por
 * operação, sem {@code PriorityQueue} e sem alocação por frame.
 */
public final class GeodesicField {

  /** Baldes de 1 mm. Precisa ser maior que o maior peso de aresta possível. */
  private static final int BUCKET_COUNT = 256;

  private static final int UNREACHABLE = Integer.MAX_VALUE;

  /** Extremos mais próximos que isto do seed não são membros; são ruído de superfície. */
  private static final int MIN_EXTREMITY_DIST_MM = 120;

  /** Cada célula pode ser inserida uma vez por aresta de entrada (4-vizinhança), mais o seed. */
  private static final int ENTRIES_PER_CELL = 4;

  private int[] dist = new int[0];
  private int[] prev = new int[0];
  private boolean[] settled = new boolean[0];
  private boolean[] suppressed = new boolean[0];
  private int[] nodes = new int[0];

  /**
   * Arena de entradas da fila de baldes.
   *
   * <p>O ponteiro de encadeamento é por <em>entrada</em>, não por nó. Com um ponteiro por nó, a
   * segunda inserção do mesmo nó (quando sua distância melhora) sobrescreve o encadeamento da
   * primeira e trunca a lista do balde anterior, perdendo nós inteiros — a propagação morria a poucos
   * centímetros do seed. Entradas duplicadas são inofensivas: a obsoleta cai num balde posterior e é
   * descartada pelo teste de {@code settled}.
   */
  private int[] entryNode = new int[0];

  private int[] entryNext = new int[0];
  private int entryCount;
  private final int[] bucketHead = new int[BUCKET_COUNT];

  /** Estruturas da busca local usada na supressão de membro. */
  private int[] localDist = new int[0];

  private int[] touched = new int[0];
  private int[] localEntryNode = new int[0];
  private int[] localEntryNext = new int[0];
  private int localEntryCount;
  private int pendingLocalPushes;
  private final int[] localBucketHead = new int[BUCKET_COUNT];

  private int[] extremities = new int[8];
  private int extremityCount;
  private int nodeCount;
  private int seedCell;
  private DepthPointCloud cloud;

  private void ensureCapacity(int cells) {
    if (dist.length >= cells) {
      return;
    }
    dist = new int[cells];
    prev = new int[cells];
    settled = new boolean[cells];
    suppressed = new boolean[cells];
    nodes = new int[cells];
    entryNode = new int[(cells * ENTRIES_PER_CELL) + 1];
    entryNext = new int[(cells * ENTRIES_PER_CELL) + 1];
    localDist = new int[cells];
    touched = new int[cells];
    localEntryNode = new int[(cells * ENTRIES_PER_CELL) + 1];
    localEntryNext = new int[(cells * ENTRIES_PER_CELL) + 1];
    Arrays.fill(localDist, UNREACHABLE);
  }

  /**
   * Propaga o campo geodésico a partir de {@code seed} por todas as células do rótulo informado.
   *
   * @return quantidade de nós alcançados.
   */
  public int compute(
      DepthPointCloud pointCloud, MetricBodySegmenter segmenter, int label, int seed) {
    this.cloud = pointCloud;
    this.seedCell = seed;
    ensureCapacity(pointCloud.getCellCount());
    collectNodes(pointCloud, segmenter, label);
    if (nodeCount == 0 || segmenter.labelAt(seed) != label) {
      return 0;
    }
    Arrays.fill(bucketHead, -1);
    entryCount = 0;
    dist[seed] = 0;
    int queued = push(seed, 0);
    int cursor = 0;
    while (queued > 0) {
      while (bucketHead[cursor] == -1) {
        cursor = (cursor + 1) & (BUCKET_COUNT - 1);
      }
      int entry = bucketHead[cursor];
      bucketHead[cursor] = entryNext[entry];
      int node = entryNode[entry];
      queued--;
      if (settled[node]) {
        continue;
      }
      settled[node] = true;
      queued += relaxNeighbors(pointCloud, segmenter, label, node);
    }
    return nodeCount;
  }

  private void collectNodes(
      DepthPointCloud pointCloud, MetricBodySegmenter segmenter, int label) {
    nodeCount = 0;
    for (int i = 0; i < pointCloud.getCellCount(); i++) {
      if (segmenter.labelAt(i) != label) {
        continue;
      }
      nodes[nodeCount++] = i;
      dist[i] = UNREACHABLE;
      prev[i] = -1;
      settled[i] = false;
      suppressed[i] = false;
    }
  }

  /** Insere uma entrada no balde correspondente à distância; devolve 1 para o contador da fila. */
  private int push(int node, int distanceMm) {
    if (entryCount >= entryNode.length) {
      return 0;
    }
    int bucket = distanceMm & (BUCKET_COUNT - 1);
    int entry = entryCount++;
    entryNode[entry] = node;
    entryNext[entry] = bucketHead[bucket];
    bucketHead[bucket] = entry;
    return 1;
  }

  /** Relaxa os 4 vizinhos de grade que pertencem ao mesmo corpo. */
  private int relaxNeighbors(
      DepthPointCloud pointCloud, MetricBodySegmenter segmenter, int label, int node) {
    int gridW = pointCloud.getGridWidth();
    int gridH = pointCloud.getGridHeight();
    int gx = node % gridW;
    int gy = node / gridW;
    int pushed = 0;
    if (gx > 0) {
      pushed += relax(pointCloud, segmenter, label, node, node - 1);
    }
    if (gx < gridW - 1) {
      pushed += relax(pointCloud, segmenter, label, node, node + 1);
    }
    if (gy > 0) {
      pushed += relax(pointCloud, segmenter, label, node, node - gridW);
    }
    if (gy < gridH - 1) {
      pushed += relax(pointCloud, segmenter, label, node, node + gridW);
    }
    return pushed;
  }

  private int relax(
      DepthPointCloud pointCloud,
      MetricBodySegmenter segmenter,
      int label,
      int from,
      int to) {
    if (segmenter.labelAt(to) != label || settled[to]) {
      return 0;
    }
    int weight = Math.max(1, Math.round(pointCloud.distance(from, to) * 1000f));
    if (weight >= BUCKET_COUNT) {
      return 0;
    }
    int candidate = dist[from] + weight;
    if (candidate >= dist[to]) {
      return 0;
    }
    dist[to] = candidate;
    prev[to] = from;
    return push(to, candidate);
  }

  /**
   * Extrai até {@code maxCount} extremos geodésicos, suprimindo o membro inteiro de cada um.
   *
   * <p>A supressão é <b>geodésica</b>, não euclidiana. Apagar uma esfera de 25 cm ao redor de um pé
   * remove só a ponta do pé: o tornozelo, a canela e o joelho continuam sendo máximos locais e ocupam
   * as vagas seguintes. Resultado observado no fixture: os cinco extremos eram cinco pontos das duas
   * pernas e nenhuma mão entrava na lista. Suprimindo por distância ao longo do corpo, cada iteração
   * elimina um membro completo e os extremos convergem para cabeça, duas mãos e dois pés.
   *
   * @param suppressRadiusMm alcance da supressão ao longo do corpo, em milímetros; deve ser um pouco
   *     maior que um membro e menor que a distância de um punho até a cabeça.
   * @return quantidade de extremos encontrados.
   */
  public int extractExtremities(int maxCount, int suppressRadiusMm) {
    extremityCount = 0;
    if (extremities.length < maxCount) {
      extremities = new int[maxCount];
    }
    for (int slot = 0; slot < maxCount; slot++) {
      int best = findFarthestUnsuppressed();
      if (best < 0) {
        break;
      }
      extremities[extremityCount++] = best;
      suppressGeodesicNeighborhood(best, suppressRadiusMm);
    }
    return extremityCount;
  }

  /**
   * Marca como suprimido todo nó a menos de {@code radiusMm} de {@code center} medido pelo corpo.
   *
   * <p>Busca local com a mesma fila de baldes, limitada ao raio: só percorre o membro, então o custo é
   * proporcional ao tamanho do membro e não ao do corpo.
   */
  private void suppressGeodesicNeighborhood(int center, int radiusMm) {
    int touchedCount = 0;
    Arrays.fill(localBucketHead, -1);
    localEntryCount = 0;
    localDist[center] = 0;
    touched[touchedCount++] = center;
    int queued = pushLocal(center, 0);
    int cursor = 0;
    while (queued > 0) {
      while (localBucketHead[cursor] == -1) {
        cursor = (cursor + 1) & (BUCKET_COUNT - 1);
      }
      int entry = localBucketHead[cursor];
      localBucketHead[cursor] = localEntryNext[entry];
      int node = localEntryNode[entry];
      queued--;
      suppressed[node] = true;
      touchedCount = relaxLocalNeighbors(node, radiusMm, touchedCount);
      queued += pendingLocalPushes;
    }
    for (int i = 0; i < touchedCount; i++) {
      localDist[touched[i]] = UNREACHABLE;
    }
  }

  private int relaxLocalNeighbors(int node, int radiusMm, int touchedCount) {
    pendingLocalPushes = 0;
    int gridW = cloud.getGridWidth();
    int gx = node % gridW;
    int gy = node / gridW;
    int count = touchedCount;
    if (gx > 0) {
      count = relaxLocal(node, node - 1, radiusMm, count);
    }
    if (gx < gridW - 1) {
      count = relaxLocal(node, node + 1, radiusMm, count);
    }
    if (gy > 0) {
      count = relaxLocal(node, node - gridW, radiusMm, count);
    }
    if (gy < cloud.getGridHeight() - 1) {
      count = relaxLocal(node, node + gridW, radiusMm, count);
    }
    return count;
  }

  private int relaxLocal(int from, int to, int radiusMm, int touchedCount) {
    if (dist[to] == UNREACHABLE) {
      return touchedCount;
    }
    int weight = Math.max(1, Math.round(cloud.distance(from, to) * 1000f));
    if (weight >= BUCKET_COUNT) {
      return touchedCount;
    }
    int candidate = localDist[from] + weight;
    if (candidate > radiusMm || candidate >= localDist[to]) {
      return touchedCount;
    }
    int count = touchedCount;
    if (localDist[to] == UNREACHABLE) {
      touched[count++] = to;
    }
    localDist[to] = candidate;
    pendingLocalPushes += pushLocal(to, candidate);
    return count;
  }

  private int pushLocal(int node, int distanceMm) {
    if (localEntryCount >= localEntryNode.length) {
      return 0;
    }
    int bucket = distanceMm & (BUCKET_COUNT - 1);
    int entry = localEntryCount++;
    localEntryNode[entry] = node;
    localEntryNext[entry] = localBucketHead[bucket];
    localBucketHead[bucket] = entry;
    return 1;
  }

  private int findFarthestUnsuppressed() {
    int best = -1;
    int bestDist = MIN_EXTREMITY_DIST_MM;
    for (int n = 0; n < nodeCount; n++) {
      int node = nodes[n];
      if (suppressed[node] || dist[node] == UNREACHABLE) {
        continue;
      }
      if (dist[node] > bestDist) {
        bestDist = dist[node];
        best = node;
      }
    }
    return best;
  }



  /**
   * Caminha de volta pelo caminho geodésico a partir de {@code fromCell} até acumular
   * {@code backDistanceMm} de percurso.
   *
   * <p>É assim que ombro e cotovelo são encontrados: percorrendo o braço de verdade. O pipeline
   * antigo colocava o cotovelo no ponto médio entre ombro e mão, o que só é correto com o braço
   * completamente esticado.
   *
   * @return célula encontrada, ou a raiz do caminho se o percurso terminar antes.
   */
  public int walkBack(int fromCell, int backDistanceMm) {
    if (fromCell < 0 || dist[fromCell] == UNREACHABLE) {
      return fromCell;
    }
    int target = dist[fromCell] - backDistanceMm;
    int current = fromCell;
    while (dist[current] > target) {
      int parent = prev[current];
      if (parent < 0) {
        return current;
      }
      current = parent;
    }
    return current;
  }

  public int getNodeCount() {
    return nodeCount;
  }

  public int getSeedCell() {
    return seedCell;
  }

  public int getExtremityCount() {
    return extremityCount;
  }

  public int getExtremity(int index) {
    return extremities[index];
  }

  /** Distância geodésica em milímetros a partir do seed. */
  public int distanceMm(int cell) {
    return dist[cell];
  }

  public boolean isReachable(int cell) {
    return dist[cell] != UNREACHABLE;
  }

  /**
   * True quando a célula pertence a um membro já consumido pela extração de extremos.
   *
   * <p>Depois de extrair mãos e pés, o que sobra não suprimido é tronco e cabeça. Isso dá um jeito
   * topológico de encontrar o crânio sem depender de janelas laterais em torno do eixo do corpo.
   */
  public boolean isSuppressed(int cell) {
    return suppressed[cell];
  }

  /** Nó do corpo na posição informada da lista compacta. */
  public int nodeAt(int index) {
    return nodes[index];
  }
}
