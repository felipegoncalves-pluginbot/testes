package com.felipe.elftemplate.tracking;

/**
 * Modelo de dados com os resultados de rastreamento do jogador, centróide, articulações e gestos.
 */
public class TrackingResult {
  public boolean isPlayerPresent = false;
  public boolean hasFeetInFrame = false;
  public boolean hasLegsInFrame = false;
  public float playerCentroidX = 0.5f;
  public float playerCentroidY = 0.5f;
  public int playerDistanceZ = 0;

  public Joint head = new Joint(0.5f, 0.2f, 0);
  public Joint neck = new Joint(0.5f, 0.3f, 0);
  public Joint spine = new Joint(0.5f, 0.5f, 0);
  public Joint leftShoulder = new Joint(0.35f, 0.32f, 0);
  public Joint leftElbow = new Joint(0.25f, 0.45f, 0);
  public Joint leftHand = new Joint(0.20f, 0.55f, 0);
  public Joint rightShoulder = new Joint(0.65f, 0.32f, 0);
  public Joint rightElbow = new Joint(0.75f, 0.45f, 0);
  public Joint rightHand = new Joint(0.80f, 0.55f, 0);
  public Joint leftHip = new Joint(0.40f, 0.70f, 0);
  public Joint rightHip = new Joint(0.60f, 0.70f, 0);
  public Joint leftKnee = new Joint(0.38f, 0.80f, 0);
  public Joint rightKnee = new Joint(0.62f, 0.80f, 0);
  public Joint leftFoot = new Joint(0.38f, 0.95f, 0);
  public Joint rightFoot = new Joint(0.62f, 0.95f, 0);

  public float leftHandX = 0.20f;
  public float leftHandY = 0.55f;
  public float rightHandX = 0.80f;
  public float rightHandY = 0.55f;

  public boolean isLeftHandRaised = false;
  public boolean isRightHandRaised = false;
  public boolean isLeftHandOpen = true;
  public boolean isRightHandOpen = true;
  /** Elevação normalizada 0=abaixado, 1=totalmente levantado (EMA + Schmitt no tracker). */
  public float leftHandElevation = 0f;
  public float rightHandElevation = 0f;
  public boolean isJumping = false;
  public boolean isDucking = false;

  public KinectTrackingEngine.GestureType activeGesture = KinectTrackingEngine.GestureType.IDLE;
  public final TrackingDiagnostics diagnostics = new TrackingDiagnostics();

  public void reset() {
    isPlayerPresent = false;
    hasFeetInFrame = false;
    hasLegsInFrame = false;
    playerCentroidX = 0.5f;
    playerCentroidY = 0.5f;
    playerDistanceZ = 0;
    head.set(0.5f, 0.2f, 0);
    neck.set(0.5f, 0.3f, 0);
    spine.set(0.5f, 0.5f, 0);
    leftShoulder.set(0.35f, 0.32f, 0);
    leftElbow.set(0.25f, 0.45f, 0);
    leftHand.set(0.20f, 0.55f, 0);
    rightShoulder.set(0.65f, 0.32f, 0);
    rightElbow.set(0.75f, 0.45f, 0);
    rightHand.set(0.80f, 0.55f, 0);
    leftHip.set(0.40f, 0.70f, 0);
    rightHip.set(0.60f, 0.70f, 0);
    leftKnee.set(0.38f, 0.80f, 0);
    rightKnee.set(0.62f, 0.80f, 0);
    leftFoot.set(0.38f, 0.95f, 0);
    rightFoot.set(0.62f, 0.95f, 0);
    leftHandX = 0.20f;
    leftHandY = 0.55f;
    rightHandX = 0.80f;
    rightHandY = 0.55f;
    isLeftHandRaised = false;
    isRightHandRaised = false;
    isLeftHandOpen = true;
    isRightHandOpen = true;
    leftHandElevation = 0f;
    rightHandElevation = 0f;
    isJumping = false;
    isDucking = false;
    activeGesture = KinectTrackingEngine.GestureType.IDLE;
    diagnostics.reset();
  }

  void copySkeletonFrom(TrackingResult src) {
    if (src == null) {
      return;
    }
    isPlayerPresent = src.isPlayerPresent;
    hasFeetInFrame = src.hasFeetInFrame;
    hasLegsInFrame = src.hasLegsInFrame;
    playerCentroidX = src.playerCentroidX;
    playerCentroidY = src.playerCentroidY;
    playerDistanceZ = src.playerDistanceZ;
    head.set(src.head.x, src.head.y, src.head.z);
    neck.set(src.neck.x, src.neck.y, src.neck.z);
    spine.set(src.spine.x, src.spine.y, src.spine.z);
    leftShoulder.set(src.leftShoulder.x, src.leftShoulder.y, src.leftShoulder.z);
    leftElbow.set(src.leftElbow.x, src.leftElbow.y, src.leftElbow.z);
    leftHand.set(src.leftHand.x, src.leftHand.y, src.leftHand.z);
    rightShoulder.set(src.rightShoulder.x, src.rightShoulder.y, src.rightShoulder.z);
    rightElbow.set(src.rightElbow.x, src.rightElbow.y, src.rightElbow.z);
    rightHand.set(src.rightHand.x, src.rightHand.y, src.rightHand.z);
    leftHip.set(src.leftHip.x, src.leftHip.y, src.leftHip.z);
    rightHip.set(src.rightHip.x, src.rightHip.y, src.rightHip.z);
    leftKnee.set(src.leftKnee.x, src.leftKnee.y, src.leftKnee.z);
    rightKnee.set(src.rightKnee.x, src.rightKnee.y, src.rightKnee.z);
    leftFoot.set(src.leftFoot.x, src.leftFoot.y, src.leftFoot.z);
    rightFoot.set(src.rightFoot.x, src.rightFoot.y, src.rightFoot.z);
    leftHandX = src.leftHandX;
    leftHandY = src.leftHandY;
    rightHandX = src.rightHandX;
    rightHandY = src.rightHandY;
    isLeftHandRaised = src.isLeftHandRaised;
    isRightHandRaised = src.isRightHandRaised;
    leftHandElevation = src.leftHandElevation;
    rightHandElevation = src.rightHandElevation;
    diagnostics.isPoseFusionActive = src.diagnostics.isPoseFusionActive;
  }

  /** Cópia completa para merge local/remoto sem alocar no hot path. */
  public void applyFrom(TrackingResult src) {
    if (src == null) {
      return;
    }
    copySkeletonFrom(src);
    isLeftHandOpen = src.isLeftHandOpen;
    isRightHandOpen = src.isRightHandOpen;
    isJumping = src.isJumping;
    isDucking = src.isDucking;
    activeGesture = src.activeGesture;
    leftHandX = src.leftHandX;
    leftHandY = src.leftHandY;
    rightHandX = src.rightHandX;
    rightHandY = src.rightHandY;
  }
}
