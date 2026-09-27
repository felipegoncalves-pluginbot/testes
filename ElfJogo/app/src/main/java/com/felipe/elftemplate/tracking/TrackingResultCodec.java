package com.felipe.elftemplate.tracking;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Serialização JSON v1 de {@link TrackingResult} para o protocolo espelho. */
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

  public static void applyJson(JSONObject json, TrackingResult out) throws JSONException {
    if (json == null || out == null) {
      return;
    }
    out.isPlayerPresent = json.optBoolean("present", false);
    out.hasFeetInFrame = json.optBoolean("hasFeet", false);
    out.hasLegsInFrame = json.optBoolean("hasLegs", false);
    JSONArray centroid = json.optJSONArray("centroid");
    if (centroid != null && centroid.length() >= 2) {
      out.playerCentroidX = (float) centroid.getDouble(0);
      out.playerCentroidY = (float) centroid.getDouble(1);
    }
    out.playerDistanceZ = json.optInt("distanceZ", 0);
    out.leftHandElevation = (float) json.optDouble("leftElev", 0.0);
    out.rightHandElevation = (float) json.optDouble("rightElev", 0.0);
    out.isLeftHandRaised = json.optBoolean("leftRaised", false);
    out.isRightHandRaised = json.optBoolean("rightRaised", false);
    out.isLeftHandOpen = json.optBoolean("leftOpen", true);
    out.isRightHandOpen = json.optBoolean("rightOpen", true);
    out.isJumping = json.optBoolean("jumping", false);
    out.isDucking = json.optBoolean("ducking", false);
    out.activeGesture = parseGesture(json.optString("gesture", "IDLE"));
    applyJoints(json.optJSONObject("joints"), out);
    out.leftHandX = out.leftHand.x;
    out.leftHandY = out.leftHand.y;
    out.rightHandX = out.rightHand.x;
    out.rightHandY = out.rightHand.y;
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

  private static void applyJoints(JSONObject joints, TrackingResult out) throws JSONException {
    if (joints == null) {
      return;
    }
    readJoint(joints, "head", out.head);
    readJoint(joints, "neck", out.neck);
    readJoint(joints, "spine", out.spine);
    readJoint(joints, "leftShoulder", out.leftShoulder);
    readJoint(joints, "leftElbow", out.leftElbow);
    readJoint(joints, "leftHand", out.leftHand);
    readJoint(joints, "rightShoulder", out.rightShoulder);
    readJoint(joints, "rightElbow", out.rightElbow);
    readJoint(joints, "rightHand", out.rightHand);
    readJoint(joints, "leftHip", out.leftHip);
    readJoint(joints, "rightHip", out.rightHip);
    readJoint(joints, "leftKnee", out.leftKnee);
    readJoint(joints, "rightKnee", out.rightKnee);
    readJoint(joints, "leftFoot", out.leftFoot);
    readJoint(joints, "rightFoot", out.rightFoot);
  }

  private static void readJoint(JSONObject joints, String key, Joint joint) throws JSONException {
    JSONArray arr = joints.optJSONArray(key);
    if (arr == null || arr.length() < 3) {
      return;
    }
    joint.set((float) arr.getDouble(0), (float) arr.getDouble(1), arr.getInt(2));
  }

  private static KinectTrackingEngine.GestureType parseGesture(String name) {
    try {
      return KinectTrackingEngine.GestureType.valueOf(name);
    } catch (Throwable ignored) {
      return KinectTrackingEngine.GestureType.IDLE;
    }
  }
}
