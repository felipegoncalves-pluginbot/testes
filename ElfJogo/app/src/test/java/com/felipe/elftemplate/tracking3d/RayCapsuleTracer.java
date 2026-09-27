package com.felipe.elftemplate.tracking3d;

/**
 * Cápsulas 3D no referencial da câmera e interseção analítica com raios.
 *
 * <p>Base do fixture de profundidade: um corpo é um conjunto de cápsulas (segmento + raio), e o mapa
 * de profundidade é o resultado de lançar um raio por pixel e guardar o Z do ponto mais próximo. Isso
 * produz perspectiva correta, auto-oclusão e sombreamento de silhueta reais — ao contrário dos
 * fixtures antigos, que desenhavam retângulos em uma grade de 64x48 e por isso nunca exercitaram a
 * geometria que o sensor de verdade entrega em 640x480.
 */
final class RayCapsuleTracer {

  /**
   * Um corpo rotulado gasta ~25 cápsulas (cada zona articular é a sua própria cápsula), então 48
   * deixa folga para uma segunda pessoa parcial na cena.
   */
  private static final int MAX_CAPSULES = 48;

  private final float[] startX = new float[MAX_CAPSULES];
  private final float[] startY = new float[MAX_CAPSULES];
  private final float[] startZ = new float[MAX_CAPSULES];
  private final float[] endX = new float[MAX_CAPSULES];
  private final float[] endY = new float[MAX_CAPSULES];
  private final float[] endZ = new float[MAX_CAPSULES];
  private final float[] radius = new float[MAX_CAPSULES];

  /** Parte do corpo que cada cápsula representa; é a verdade de campo do treino do classificador. */
  private final int[] label = new int[MAX_CAPSULES];

  private int capsuleCount;

  void clear() {
    capsuleCount = 0;
  }

  int size() {
    return capsuleCount;
  }

  /** Adiciona uma cápsula já em coordenadas de câmera (X direita, Y baixo, Z frente). */
  void addCapsule(
      float x1, float y1, float z1, float x2, float y2, float z2, float capsuleRadius) {
    addCapsule(x1, y1, z1, x2, y2, z2, capsuleRadius, BodyPart.BACKGROUND);
  }

  /** Versão rotulada: o rótulo é o que o renderizador escreve no mapa de partes. */
  void addCapsule(
      float x1,
      float y1,
      float z1,
      float x2,
      float y2,
      float z2,
      float capsuleRadius,
      int partLabel) {
    if (capsuleCount >= MAX_CAPSULES) {
      throw new IllegalStateException("limite de cápsulas do fixture excedido");
    }
    startX[capsuleCount] = x1;
    startY[capsuleCount] = y1;
    startZ[capsuleCount] = z1;
    endX[capsuleCount] = x2;
    endY[capsuleCount] = y2;
    endZ[capsuleCount] = z2;
    radius[capsuleCount] = capsuleRadius;
    label[capsuleCount] = partLabel;
    capsuleCount++;
  }

  int labelOf(int capsule) {
    return label[capsule];
  }

  /**
   * Distância radial da origem até a cápsula ao longo do raio unitário, ou -1 se não houver hit.
   *
   * <p>Resolve a quadrática do cilindro infinito ao redor do eixo do segmento e, quando o ponto de
   * contato cai fora do trecho, testa a esfera da tampa correspondente.
   */
  float intersect(int capsule, float dirX, float dirY, float dirZ) {
    float axisX = endX[capsule] - startX[capsule];
    float axisY = endY[capsule] - startY[capsule];
    float axisZ = endZ[capsule] - startZ[capsule];
    float toStartX = -startX[capsule];
    float toStartY = -startY[capsule];
    float toStartZ = -startZ[capsule];
    float axisDotAxis = dot(axisX, axisY, axisZ, axisX, axisY, axisZ);
    float axisDotDir = dot(axisX, axisY, axisZ, dirX, dirY, dirZ);
    float axisDotToStart = dot(axisX, axisY, axisZ, toStartX, toStartY, toStartZ);
    float dirDotToStart = dot(dirX, dirY, dirZ, toStartX, toStartY, toStartZ);
    float toStartSq = dot(toStartX, toStartY, toStartZ, toStartX, toStartY, toStartZ);
    float radiusSq = radius[capsule] * radius[capsule];

    float quadA = axisDotAxis - (axisDotDir * axisDotDir);
    float quadB = (axisDotAxis * dirDotToStart) - (axisDotToStart * axisDotDir);
    float quadC = (axisDotAxis * toStartSq) - (axisDotToStart * axisDotToStart) - (radiusSq * axisDotAxis);
    float discriminant = (quadB * quadB) - (quadA * quadC);
    if (discriminant < 0f) {
      return -1f;
    }
    if (quadA != 0f) {
      float hit = (-quadB - (float) Math.sqrt(discriminant)) / quadA;
      float alongAxis = axisDotToStart + (hit * axisDotDir);
      if (alongAxis > 0f && alongAxis < axisDotAxis && hit > 0f) {
        return hit;
      }
    }
    return intersectCaps(capsule, dirX, dirY, dirZ, radiusSq);
  }

  /** Testa as duas esferas das extremidades e devolve a mais próxima. */
  private float intersectCaps(
      int capsule, float dirX, float dirY, float dirZ, float radiusSq) {
    float first =
        intersectSphere(
            startX[capsule], startY[capsule], startZ[capsule], dirX, dirY, dirZ, radiusSq);
    float second =
        intersectSphere(endX[capsule], endY[capsule], endZ[capsule], dirX, dirY, dirZ, radiusSq);
    if (first < 0f) {
      return second;
    }
    if (second < 0f) {
      return first;
    }
    return Math.min(first, second);
  }

  private static float intersectSphere(
      float centerX,
      float centerY,
      float centerZ,
      float dirX,
      float dirY,
      float dirZ,
      float radiusSq) {
    float linear = -dot(dirX, dirY, dirZ, centerX, centerY, centerZ);
    float constant = dot(centerX, centerY, centerZ, centerX, centerY, centerZ) - radiusSq;
    float discriminant = (linear * linear) - constant;
    if (discriminant <= 0f) {
      return -1f;
    }
    float hit = -linear - (float) Math.sqrt(discriminant);
    return hit > 0f ? hit : -1f;
  }

  private static float dot(float ax, float ay, float az, float bx, float by, float bz) {
    return (ax * bx) + (ay * by) + (az * bz);
  }
}
