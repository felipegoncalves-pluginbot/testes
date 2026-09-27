package com.felipe.elftemplate.tracking3d;

/**
 * Constrói um esqueleto métrico a partir do campo geodésico de um corpo segmentado.
 *
 * <p>Ordem do ajuste: seed no tronco, campo geodésico, extremos, cabeça, eixo/ombros, membros pelos
 * caminhos reais e, por último, quadril e coluna. Cada junta recebe uma confiança que distingue
 * "medido no sensor" de "sintetizado por proporção", coisa que o pipeline antigo não expunha — lá um
 * braço invisível produzia uma mão em posição inventada com a mesma aparência de uma mão detectada, e
 * o jogo tratava as duas igual.
 */
public final class GeodesicSkeletonFitter {

  /** Confiança de junta medida diretamente na nuvem. */
  public static final float CONFIDENCE_MEASURED = 1.0f;

  /** Confiança de junta obtida percorrendo o caminho geodésico do membro. */
  public static final float CONFIDENCE_PATH = 0.7f;

  /** Confiança de junta sintetizada apenas por proporção antropométrica. */
  public static final float CONFIDENCE_SYNTHETIC = 0.2f;

  /**
   * Piso para tratar uma junta como observação, e não como palpite.
   *
   * <p>Fica entre a junta sintetizada (0,2) e a obtida por caminho geodésico (0,7). É a fronteira que
   * os consumidores consultam para decidir se reagem: acionar asa ou desenhar osso a partir de junta
   * inventada foi o que produziu movimento errático no robô.
   */
  public static final float CONFIDENCE_USABLE = 0.5f;

  /**
   * Duas mãos, dois pés e uma vaga de folga.
   *
   * <p>A cabeça não é procurada como extremo: ela é o que sobra depois de os membros serem suprimidos.
   * Extrair mais extremos que isso é contraproducente, porque cada supressão adicional avança sobre o
   * tronco e pode alcançar o crânio.
   */
  private static final int MAX_EXTREMITIES = 5;

  /**
   * Alcance da supressão de membro, como fração da estatura.
   *
   * <p>Fica logo abaixo do comprimento de um braço (0,33 da estatura): apaga antebraço e cotovelo,
   * mas para antes do ombro. Isso é deliberado. Um alcance maior parece melhor no papel, mas quando o
   * braço está relaxado encostado no tronco o caminho geodésico até o punho desce pela frente do
   * tronco em vez de percorrer o braço, e uma supressão longa avança sobre o tronco e engole a cabeça
   * — a cabeça passava a ser detectada na altura do ombro. Curto e previsível é melhor: o cotovelo que
   * eventualmente sobra como extremo é descartado depois pelo teste de âncora do {@link LimbResolver}.
   */
  private static final float LIMB_SUPPRESS_RATIO = 0.30f;

  /** Teto absoluto da supressão, em metros. */
  private static final float LIMB_SUPPRESS_MAX_M = 0.62f;

  /** Janela lateral ao redor do eixo do corpo: rede de segurança, não critério principal. */
  private static final float HEAD_AXIS_WINDOW_M = 0.26f;

  /**
   * Meia largura da coluna do eixo usada na estatura provisória.
   *
   * <p>11 cm cobre um crânio inteiro e fica bem longe do ombro, que está a 20 cm ou mais do eixo.
   */
  private static final float AXIS_COLUMN_HALF_WIDTH_M = 0.11f;

  /** Janela em profundidade para o topo da cabeça, evitando pegar a parede atrás. */
  private static final float HEAD_DEPTH_WINDOW_M = 0.35f;

  /**
   * Fração do intervalo entre "cabeça" e "punho" onde fica a fronteira de decisão.
   *
   * <p>O percurso do tronco até a cabeça contorna ombro e pescoço, então excede a distância em linha
   * reta; já o percurso até um punho erguido é quase o dobro. Colocar o corte no meio do caminho entre
   * as duas expectativas separa as classes com folga dos dois lados, em vez de depender de uma
   * tolerância escolhida a dedo.
   */
  private static final float HEAD_VS_WRIST_BOUNDARY = 0.5f;

  /** Raio de agregação para achar o centro da cabeça a partir do ponto mais alto. */
  private static final float HEAD_CLUSTER_RADIUS_M = 0.10f;

  private final GeodesicField field = new GeodesicField();
  private final LimbResolver limbResolver = new LimbResolver();

  private float axisX;
  private float torsoZ;
  private float stature;
  private float headX;
  private float headY;
  private float headZ;

  /**
   * Ajusta o esqueleto ao aglomerado informado.
   *
   * @return true se o ajuste produziu um esqueleto utilizável.
   */
  public boolean fit(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      BodyCluster cluster,
      MetricSkeleton skeleton,
      long timestampMs) {
    skeleton.reset();
    if (cloud == null || segmenter == null || cluster == null || cluster.pointCount <= 0) {
      return false;
    }
    axisX = cluster.bodyAxisX;
    torsoZ = cluster.centroidZ;
    stature = estimateProvisionalStature(cloud, segmenter, cluster);

    int seed = findSeedCell(cloud, segmenter, cluster);
    if (seed < 0 || field.compute(cloud, segmenter, cluster.label, seed) <= 0) {
      return false;
    }
    float suppressM = Math.min(LIMB_SUPPRESS_MAX_M, LIMB_SUPPRESS_RATIO * stature);
    field.extractExtremities(MAX_EXTREMITIES, (int) (suppressM * 1000f));

    resolveHead(cloud, segmenter, cluster);
    stature = refineStatureFromHead(cluster);
    skeleton.statureM = stature;
    skeleton.torsoDepthM = torsoZ;
    skeleton.shoulderHalfSpanM = BodyProportions.SHOULDER_HALF_SPAN * stature;
    skeleton.seated = cluster.seated;
    skeleton.headOutOfFrame = cluster.headOutOfFrame;
    skeleton.timestampMs = timestampMs;
    skeleton.set(MetricSkeleton.HEAD, headX, headY, headZ, CONFIDENCE_MEASURED);

    applyShouldersAndTorso(skeleton);
    limbResolver.resolve(cloud, segmenter, field, skeleton, axisX);
    skeleton.feetVisible = limbResolver.areFeetVisible() && !cluster.touchesFrameBottom;
    skeleton.valid = true;
    return true;
  }

  /**
   * Estatura provisória a partir do topo da coluna estreita do eixo do corpo.
   *
   * <p>Isto existe para romper uma dependência circular que gerava um erro grande e silencioso: o raio
   * de supressão de membro e o corte geodésico da cabeça são proporcionais à estatura, mas a estatura
   * vinha do ponto mais alto do aglomerado — que com a mão erguida é o punho. Uma pessoa de 1,75 m era
   * medida como 2,03 m, o raio e o corte inflavam junto, e o braço acabava vencendo a disputa pela
   * cabeça, realimentando o erro.
   *
   * <p>A coluna do eixo com meia largura de 11 cm resolve porque é geometria humana: um crânio tem no
   * máximo 20 cm de largura e um ombro fica a pelo menos 20 cm do eixo, então nenhum braço entra na
   * janela, qualquer que seja a pose.
   */
  private float estimateProvisionalStature(
      DepthPointCloud cloud, MetricBodySegmenter segmenter, BodyCluster cluster) {
    float fallback = PersonGate.statureForProportions(cluster);
    if (cluster.headOutOfFrame) {
      return fallback;
    }
    float highest = -Float.MAX_VALUE;
    int gridW = cloud.getGridWidth();
    for (int gy = cluster.minGridY; gy <= cluster.maxGridY; gy++) {
      int rowBase = gy * gridW;
      for (int gx = cluster.minGridX; gx <= cluster.maxGridX; gx++) {
        int i = rowBase + gx;
        if (segmenter.labelAt(i) != cluster.label) {
          continue;
        }
        if (Math.abs(cloud.x(i) - axisX) > AXIS_COLUMN_HALF_WIDTH_M) {
          continue;
        }
        if (Math.abs(segmenter.worldZAt(i) - torsoZ) > HEAD_DEPTH_WINDOW_M) {
          continue;
        }
        float worldY = segmenter.worldYAt(i);
        if (worldY > highest) {
          highest = worldY;
        }
      }
    }
    if (highest <= 0f) {
      return fallback;
    }
    // O topo do crânio é a própria estatura; não dividir por HEAD_CENTER_HEIGHT, que é a altura do
    // centro da cabeça e inflaria a estimativa em ~6%.
    float estimated = highest;
    if (estimated < PersonGate.MIN_STANDING_HEIGHT_M
        || estimated > PersonGate.MAX_STANDING_HEIGHT_M) {
      return fallback;
    }
    return estimated;
  }

  /**
   * Escolhe a célula mais próxima do centro geométrico do tronco.
   *
   * <p>O seed precisa ficar dentro do tronco: se cair em uma mão, todas as distâncias geodésicas
   * ficam contadas a partir do braço e a classificação de extremos desmonta.
   */
  private int findSeedCell(
      DepthPointCloud cloud, MetricBodySegmenter segmenter, BodyCluster cluster) {
    float targetY = clampToCluster(BodyProportions.TORSO_CENTER_HEIGHT * stature, cluster);
    float best = Float.MAX_VALUE;
    int bestCell = -1;
    int gridW = cloud.getGridWidth();
    for (int gy = cluster.minGridY; gy <= cluster.maxGridY; gy++) {
      int rowBase = gy * gridW;
      for (int gx = cluster.minGridX; gx <= cluster.maxGridX; gx++) {
        int i = rowBase + gx;
        if (segmenter.labelAt(i) != cluster.label) {
          continue;
        }
        float dx = cloud.x(i) - axisX;
        float dy = segmenter.worldYAt(i) - targetY;
        float dz = segmenter.worldZAt(i) - torsoZ;
        float squared = (dx * dx) + (dy * dy) + (dz * dz);
        if (squared < best) {
          best = squared;
          bestCell = i;
        }
      }
    }
    return bestCell;
  }

  private static float clampToCluster(float value, BodyCluster cluster) {
    return Math.max(cluster.minWorldY, Math.min(cluster.maxWorldY, value));
  }

  /**
   * Recalcula a estatura a partir da altura da cabeça detectada.
   *
   * <p>O topo do aglomerado não serve como estatura: com a mão erguida, o ponto mais alto do corpo é o
   * punho, e uma pessoa de 1,75 m era medida como 2,03 m. Esse erro contaminava a envergadura de
   * ombros, todas as quedas verticais e a classificação de membros. A cabeça, uma vez localizada, é
   * uma âncora estável — a razão cabeça/estatura varia pouco entre adultos e crianças.
   */
  private float refineStatureFromHead(BodyCluster cluster) {
    if (cluster.headOutOfFrame) {
      return stature;
    }
    float fromHead = headY / BodyProportions.HEAD_CENTER_HEIGHT;
    if (fromHead < PersonGate.MIN_STANDING_HEIGHT_M
        || fromHead > PersonGate.MAX_STANDING_HEIGHT_M) {
      return stature;
    }
    return fromHead;
  }

  /**
   * Localiza o centro da cabeça.
   *
   * <p>Procura o ponto mais alto na coluna do eixo do corpo, mas descarta candidatos cuja distância
   * geodésica excede a esperada para a cabeça. É esse teste que impede que uma mão erguida acima da
   * cabeça — ou o topo de um encosto de cadeira ainda colado ao corpo — seja tomado como crânio, o
   * bug que gerava HANDS_UP fantasma no sistema antigo.
   */
  private void resolveHead(
      DepthPointCloud cloud, MetricBodySegmenter segmenter, BodyCluster cluster) {
    float headGeodesic = BodyProportions.torsoToHeadGeodesic(stature);
    float wristGeodesic = BodyProportions.torsoToWristGeodesic(stature);
    int maxHeadGeodesicMm =
        (int)
            ((headGeodesic + ((wristGeodesic - headGeodesic) * HEAD_VS_WRIST_BOUNDARY)) * 1000f);
    int topCell = findHeadTopCell(cloud, segmenter, cluster, maxHeadGeodesicMm);
    if (topCell < 0) {
      headX = axisX;
      headY = cluster.maxWorldY;
      headZ = torsoZ;
      return;
    }
    aggregateHeadCenter(cloud, segmenter, cluster, topCell);
  }

  /**
   * Ponto mais alto do corpo que não pertence a nenhum membro e está perto do tronco pelo corpo.
   *
   * <p>Três filtros combinados, todos topológicos ou métricos:
   *
   * <ul>
   *   <li><b>Não suprimido</b>: mãos, antebraços e cotovelos já saíram na extração de extremos. Sem
   *       isso, o cotovelo de um braço erguido reto fica alguns centímetros mais alto que o crânio e
   *       ganhava a disputa.
   *   <li><b>Distância geodésica abaixo da fronteira cabeça/punho</b>: descarta qualquer coisa que
   *       exija percorrer um membro para ser alcançada.
   *   <li><b>Janela lateral e em profundidade</b>: rede de segurança, agora larga o suficiente para
   *       não depender da precisão do eixo.
   * </ul>
   */
  private int findHeadTopCell(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      BodyCluster cluster,
      int maxGeodesicMm) {
    float bestY = -Float.MAX_VALUE;
    int bestCell = -1;
    // Percorre a lista compacta de nós do corpo, não a grade inteira.
    for (int n = 0; n < field.getNodeCount(); n++) {
      int i = field.nodeAt(n);
      if (field.isSuppressed(i) || field.distanceMm(i) > maxGeodesicMm) {
        continue;
      }
      if (Math.abs(cloud.x(i) - axisX) > HEAD_AXIS_WINDOW_M) {
        continue;
      }
      if (Math.abs(segmenter.worldZAt(i) - torsoZ) > HEAD_DEPTH_WINDOW_M) {
        continue;
      }
      float worldY = segmenter.worldYAt(i);
      if (worldY > bestY) {
        bestY = worldY;
        bestCell = i;
      }
    }
    return bestCell;
  }

  /** Média das células ao redor do topo detectado, para o centro da cabeça não tremer com ruído. */
  private void aggregateHeadCenter(
      DepthPointCloud cloud, MetricBodySegmenter segmenter, BodyCluster cluster, int topCell) {
    double sumX = 0;
    double sumY = 0;
    double sumZ = 0;
    int count = 0;
    float radiusSquared = HEAD_CLUSTER_RADIUS_M * HEAD_CLUSTER_RADIUS_M;
    for (int n = 0; n < field.getNodeCount(); n++) {
      int i = field.nodeAt(n);
      // Comparação em distância ao quadrado: evita uma raiz quadrada por nó do corpo.
      if (cloud.squaredDistance(topCell, i) > radiusSquared) {
        continue;
      }
      sumX += cloud.x(i);
      sumY += segmenter.worldYAt(i);
      sumZ += segmenter.worldZAt(i);
      count++;
    }
    if (count == 0) {
      headX = cloud.x(topCell);
      headY = segmenter.worldYAt(topCell);
      headZ = segmenter.worldZAt(topCell);
      return;
    }
    headX = (float) (sumX / count);
    // O topo agregado fica na casca do crânio; o centro está meio raio abaixo.
    headY = (float) (sumY / count) - (BodyProportions.HEAD_RADIUS * stature * 0.5f);
    headZ = (float) (sumZ / count);
  }

  /**
   * Posiciona ombros, pescoço, coluna e quadris ancorando na cabeça medida.
   *
   * <p>Ancorar na cabeça em vez de na altura absoluta do piso deixa o esqueleto correto mesmo se a
   * estimativa do plano do chão estiver alguns centímetros deslocada ou se a pessoa estiver sobre um
   * degrau.
   */
  private void applyShouldersAndTorso(MetricSkeleton skeleton) {
    float halfSpan = BodyProportions.SHOULDER_HALF_SPAN * stature;
    float shoulderDrop =
        (BodyProportions.HEAD_CENTER_HEIGHT - BodyProportions.SHOULDER_HEIGHT) * stature;
    float shoulderY = headY - shoulderDrop;
    // Lateralidade anatômica: o lado esquerdo da pessoa aparece com X maior na imagem.
    skeleton.set(
        MetricSkeleton.LEFT_SHOULDER, axisX + halfSpan, shoulderY, torsoZ, CONFIDENCE_PATH);
    skeleton.set(
        MetricSkeleton.RIGHT_SHOULDER, axisX - halfSpan, shoulderY, torsoZ, CONFIDENCE_PATH);
    skeleton.set(
        MetricSkeleton.NECK, axisX, shoulderY + (shoulderDrop * 0.35f), torsoZ, CONFIDENCE_PATH);

    float hipDrop = (BodyProportions.HEAD_CENTER_HEIGHT - BodyProportions.HIP_HEIGHT) * stature;
    float hipY = headY - hipDrop;
    float hipHalfSpan = BodyProportions.HIP_HALF_SPAN * stature;
    skeleton.set(MetricSkeleton.LEFT_HIP, axisX + hipHalfSpan, hipY, torsoZ, CONFIDENCE_PATH);
    skeleton.set(MetricSkeleton.RIGHT_HIP, axisX - hipHalfSpan, hipY, torsoZ, CONFIDENCE_PATH);
    skeleton.set(
        MetricSkeleton.SPINE, axisX, (shoulderY + hipY) * 0.5f, torsoZ, CONFIDENCE_PATH);
  }

  /** Campo geodésico do último ajuste; exposto para diagnóstico e testes. */
  public GeodesicField getField() {
    return field;
  }
}
