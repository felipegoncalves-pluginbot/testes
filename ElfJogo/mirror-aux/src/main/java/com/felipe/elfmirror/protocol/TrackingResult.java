package com.felipe.elfmirror.protocol;

/** Resultado de tracking serializado no protocolo espelho v1. */
public final class TrackingResult {

  public boolean isPlayerPresent;
  public boolean hasFeetInFrame;
  public boolean hasLegsInFrame;
  public float playerCentroidX = 0.5f;
  public float playerCentroidY = 0.5f;
  public int playerDistanceZ;

  public final Joint head = new Joint();
  public final Joint neck = new Joint();
  public final Joint spine = new Joint();
  public final Joint leftShoulder = new Joint();
  public final Joint leftElbow = new Joint();
  public final Joint leftHand = new Joint();
  public final Joint rightShoulder = new Joint();
  public final Joint rightElbow = new Joint();
  public final Joint rightHand = new Joint();
  public final Joint leftHip = new Joint();
  public final Joint rightHip = new Joint();
  public final Joint leftKnee = new Joint();
  public final Joint rightKnee = new Joint();
  public final Joint leftFoot = new Joint();
  public final Joint rightFoot = new Joint();

  public boolean isLeftHandRaised;
  public boolean isRightHandRaised;
  public boolean isLeftHandOpen = true;
  public boolean isRightHandOpen = true;
  public float leftHandElevation;
  public float rightHandElevation;
  public boolean isJumping;
  public boolean isDucking;
  public GestureType activeGesture = GestureType.IDLE;

  public void reset() {
    isPlayerPresent = false;
    hasFeetInFrame = false;
    hasLegsInFrame = false;
    playerCentroidX = 0.5f;
    playerCentroidY = 0.5f;
    playerDistanceZ = 0;
    isLeftHandRaised = false;
    isRightHandRaised = false;
    isLeftHandOpen = true;
    isRightHandOpen = true;
    leftHandElevation = 0f;
    rightHandElevation = 0f;
    isJumping = false;
    isDucking = false;
    activeGesture = GestureType.IDLE;
  }
}
