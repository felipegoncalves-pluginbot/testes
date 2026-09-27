package com.felipe.elftemplate.tracking3d;

import android.content.Context;

/**
 * Sobrepõe ao esqueleto geométrico as juntas propostas pelo classificador aprendido.
 *
 * <p>Junta os dois estimadores que o projeto agora tem, e a ordem de preferência é deliberada:
 * quando o classificador tem convicção, ele manda, com uma exceção: mão ou pé que a geometria mediu
 * como extremidade só são trocados se a proposta concordar com eles (ver {@code
 * EXTREMITY_DISAGREEMENT_M}). O motivo é estrutural, não de gosto. O caminho geodésico só acha um
 * membro se ele produzir um extremo na superfície do corpo, e existem poses inteiras em que isso
 * não acontece — mão apoiada no tronco, braço estendido para a frente, braço cruzando o corpo. Nos
 * casos em que o geodésico falha ele não fica impreciso, ele fica ausente, e a junta passa a ser
 * inventada por proporção. O classificador não tem esse ponto cego: ele decide pixel por pixel.
 *
 * <p>O geodésico permanece como fallback porque tem a propriedade oposta: não depende de ter visto
 * a pose antes. Onde o classificador não tem suporte suficiente, a geometria ainda entrega algo
 * plausível.
 *
 * <p>Roda antes do filtro temporal, sobre a medição crua. Filtrar e depois sobrescrever jogaria a
 * suavização no lixo em todo frame em que a fonte trocasse.
 */
public final class LearnedSkeletonRefiner {

  /**
   * Confiança mínima da proposta para ela substituir a geometria.
   *
   * <p>0,35 significa "vi ao menos um terço da área que essa parte deveria mostrar". Abaixo disso a
   * nuvem é pequena demais para o modo ser estável, e o mean shift passa a seguir ruído.
   */
  private static final float ACCEPT_CONFIDENCE = 0.35f;

  /**
   * Discordância, em metros, a partir da qual uma mão ou um pé medidos pela geometria vencem a
   * proposta.
   *
   * <p>Quando o caminho geodésico acha a extremidade, ela é a ponta real do membro na nuvem. Em
   * 1.000 poses de validação com o Astra na cabeça, substituí-la sempre pela proposta piorava
   * justamente o braço erguido e o aberto para o lado, que são os dos jogos: punho lateral de
   * 63–69% para 51–52% dentro de 15 cm. Com esta regra os punhos no geral passam de 29% para
   * 33–34%, e o braço para a frente, onde a geometria não tem extremo, continua vindo do
   * classificador.
   */
  private static final float EXTREMITY_DISAGREEMENT_M = 0.15f;

  private final BodyPartLabeler labeler = new BodyPartLabeler();
  private final BodyPartJointProposer proposer = new BodyPartJointProposer();

  private int overriddenJoints;

  /** Carrega o modelo. Sem modelo o refinador vira operação nula e o motor segue na geometria. */
  public boolean load(Context context) {
    return labeler.loadFromAssets(context);
  }

  /**
   * Carrega o modelo de um stream.
   *
   * <p>Existe para o teste medir acurácia contra o mesmo arquivo que vai no APK. Sem isto o teste
   * precisaria de um {@code Context} do Android, que não existe em teste unitário, e acabaria
   * validando um modelo treinado na hora em vez do artefato embarcado.
   */
  public boolean load(java.io.InputStream input) throws java.io.IOException {
    return labeler.load(input);
  }

  public boolean isEnabled() {
    return labeler.isModelAvailable();
  }

  /**
   * Classifica, propõe e sobrescreve as juntas aceitas.
   *
   * @return quantidade de juntas que passaram a vir do classificador
   */
  public int refine(
      DepthPointCloud cloud,
      MetricBodySegmenter segmenter,
      BodyCluster cluster,
      MetricSkeleton skeleton) {
    overriddenJoints = 0;
    if (!labeler.isModelAvailable() || skeleton == null || !skeleton.valid) {
      return 0;
    }
    if (labeler.label(cloud, segmenter, cluster) <= 0) {
      return 0;
    }
    proposer.propose(cloud, segmenter, labeler, cluster, skeleton.statureM);
    for (int joint = 0; joint < MetricSkeleton.JOINT_COUNT; joint++) {
      if (!proposer.hasProposal(joint)) {
        continue;
      }
      float confidence = proposer.confidenceOf(joint);
      if (confidence < ACCEPT_CONFIDENCE) {
        continue;
      }
      if (keepsMeasuredExtremity(skeleton, joint)) {
        continue;
      }
      skeleton.set(
          joint,
          proposer.x(joint),
          proposer.y(joint),
          proposer.z(joint),
          skeletonConfidenceFor(confidence));
      overriddenJoints++;
    }
    return overriddenJoints;
  }

  /** Mão ou pé que a geometria mediu como extremidade e dos quais a proposta discorda demais. */
  private boolean keepsMeasuredExtremity(MetricSkeleton skeleton, int joint) {
    if (!isExtremity(joint)
        || skeleton.confidence(joint) < GeodesicSkeletonFitter.CONFIDENCE_MEASURED) {
      return false;
    }
    float dx = proposer.x(joint) - skeleton.x(joint);
    float dy = proposer.y(joint) - skeleton.y(joint);
    float dz = proposer.z(joint) - skeleton.z(joint);
    float limit = EXTREMITY_DISAGREEMENT_M * EXTREMITY_DISAGREEMENT_M;
    return (dx * dx) + (dy * dy) + (dz * dz) > limit;
  }

  private static boolean isExtremity(int joint) {
    return joint == MetricSkeleton.LEFT_WRIST
        || joint == MetricSkeleton.RIGHT_WRIST
        || joint == MetricSkeleton.LEFT_ANKLE
        || joint == MetricSkeleton.RIGHT_ANKLE;
  }

  /**
   * Traduz a confiança da proposta para a escala de confiança do esqueleto.
   *
   * <p>Uma proposta aceita é medição de sensor, então o piso é a confiança de caminho (0,7) e não a de
   * síntese. Mapear a faixa aceita para [0,7 ; 1,0] preserva a graduação sem que uma junta medida
   * apareça para o consumidor como se tivesse sido inventada — que é a informação que o overlay e as
   * asas usam para decidir se reagem.
   */
  private static float skeletonConfidenceFor(float proposalConfidence) {
    float span = 1f - ACCEPT_CONFIDENCE;
    float normalized = span <= 0f ? 1f : (proposalConfidence - ACCEPT_CONFIDENCE) / span;
    if (normalized > 1f) {
      normalized = 1f;
    }
    float low = GeodesicSkeletonFitter.CONFIDENCE_PATH;
    return low + ((GeodesicSkeletonFitter.CONFIDENCE_MEASURED - low) * normalized);
  }

  public int getOverriddenJoints() {
    return overriddenJoints;
  }

  public int getLabeledCells() {
    return labeler.getLabeledCells();
  }

  public int getProposalCount() {
    return proposer.getProposalCount();
  }

  public String getLoadError() {
    return labeler.getLoadError();
  }

  /** Rotulador do último frame; usado pelo overlay de depuração e pelos testes. */
  public BodyPartLabeler getLabeler() {
    return labeler;
  }

  public BodyPartJointProposer getProposer() {
    return proposer;
  }
}
