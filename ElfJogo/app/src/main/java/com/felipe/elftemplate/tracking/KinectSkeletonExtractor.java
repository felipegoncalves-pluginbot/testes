package com.felipe.elftemplate.tracking;

/** Extrator e estimador de articulações anatômicas 3D a partir do mapa de profundidade Astra. */
public class KinectSkeletonExtractor {

  public static void computeJoints(
      TrackingResult result,
      float neckY,
      float shoulderSpan,
      boolean likelySeated,
      boolean isNearMode,
      int bodyH,
      int height,
      int maxY,
      int maxGroundRow,
      int step) {

    float shoulderY = neckY + (likelySeated ? 0.02f : 0.03f);
    result.leftShoulder.set(
        Math.max(0.04f, result.playerCentroidX - shoulderSpan),
        shoulderY,
        result.playerDistanceZ);
    result.rightShoulder.set(
        Math.min(0.96f, result.playerCentroidX + shoulderSpan),
        shoulderY,
        result.playerDistanceZ);

    float spineX = (result.leftShoulder.x + result.rightShoulder.x) * 0.5f;
    float spineY = (neckY + shoulderY) * 0.5f;
    result.spine.set(spineX, spineY, result.playerDistanceZ);

    result.leftElbow.set(
        (result.leftShoulder.x + result.leftHand.x) * 0.5f,
        (result.leftShoulder.y + result.leftHand.y) * 0.5f,
        result.playerDistanceZ);
    result.rightElbow.set(
        (result.rightShoulder.x + result.rightHand.x) * 0.5f,
        (result.rightShoulder.y + result.rightHand.y) * 0.5f,
        result.playerDistanceZ);

    // Pernas e Pés
    boolean touchesBottom = (maxY >= (maxGroundRow - step * 2));
    boolean hasSufficientHeightForLegs =
        (bodyH >= (height * 0.45f)) && !isNearMode && !likelySeated;
    result.hasFeetInFrame = (!touchesBottom && hasSufficientHeightForLegs);
    result.hasLegsInFrame = (!isNearMode && !likelySeated && (bodyH >= (height * 0.35f)));

    float hipY = result.playerCentroidY + (bodyH / (float) height) * 0.24f;
    result.leftHip.set(
        result.playerCentroidX - shoulderSpan * 0.50f, hipY, result.playerDistanceZ);
    result.rightHip.set(
        result.playerCentroidX + shoulderSpan * 0.50f, hipY, result.playerDistanceZ);

    if (result.hasFeetInFrame) {
      float footY = Math.min(0.96f, maxY / (float) height);
      result.leftFoot.set(result.leftHip.x, footY, result.playerDistanceZ);
      result.rightFoot.set(result.rightHip.x, footY, result.playerDistanceZ);
    } else {
      result.leftFoot.set(result.leftHip.x, hipY + 0.05f, result.playerDistanceZ);
      result.rightFoot.set(result.rightHip.x, hipY + 0.05f, result.playerDistanceZ);
    }
  }
}
