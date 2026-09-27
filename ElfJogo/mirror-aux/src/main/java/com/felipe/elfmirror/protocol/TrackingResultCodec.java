package com.felipe.elfmirror.protocol;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Serialização JSON v1 — espelho de {@code TrackingResultCodec} no host. */
public final class TrackingResultCodec {

  private TrackingResultCodec() {}

  public static JSONObject toJson(TrackingResult r) throws JSONException {
    JSONObject json = new JSONObject();
    json.put("present", r.isPlayerPresent);
    json.put("hasFeet", r.hasFeetInFrame);
    json.put("hasLegs", r.hasLegsInFrame);
    JSONArray centroid = new JSONArray();
    centroid.put(r.playerCentroidX);
    centroid.put(r.playerCentroidY);
    json.put("centroid", centroid);
    json.put("distanceZ", r.playerDistanceZ);
    json.put("leftElev", r.leftHandElevation);
    json.put("rightElev", r.rightHandElevation);
    json.put("leftRaised", r.isLeftHandRaised);
    json.put("rightRaised", r.isRightHandRaised);
    json.put("leftOpen", r.isLeftHandOpen);
    json.put("rightOpen", r.isRightHandOpen);
    json.put("jumping", r.isJumping);
    json.put("ducking", r.isDucking);
    json.put("gesture", r.activeGesture.name());
    json.put("joints", jointsToJson(r));
    return json;
  }

  private static JSONObject jointsToJson(TrackingResult r) throws JSONException {
    JSONObject joints = new JSONObject();
    putJoint(joints, "head", r.head);
    putJoint(joints, "neck", r.neck);
    putJoint(joints, "spine", r.spine);
    putJoint(joints, "leftShoulder", r.leftShoulder);
    putJoint(joints, "leftElbow", r.leftElbow);
    putJoint(joints, "leftHand", r.leftHand);
    putJoint(joints, "rightShoulder", r.rightShoulder);
    putJoint(joints, "rightElbow", r.rightElbow);
    putJoint(joints, "rightHand", r.rightHand);
    putJoint(joints, "leftHip", r.leftHip);
    putJoint(joints, "rightHip", r.rightHip);
    putJoint(joints, "leftKnee", r.leftKnee);
    putJoint(joints, "rightKnee", r.rightKnee);
    putJoint(joints, "leftFoot", r.leftFoot);
    putJoint(joints, "rightFoot", r.rightFoot);
    return joints;
  }

  private static void putJoint(JSONObject joints, String key, Joint joint) throws JSONException {
    JSONArray arr = new JSONArray();
    arr.put(joint.x);
    arr.put(joint.y);
    arr.put(joint.z);
    joints.put(key, arr);
  }
}
