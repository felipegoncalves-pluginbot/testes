package com.sanbot.debug;

/**
 * Snapshot desacoplado do que o Sanbot vê e comanda. Sem dependência de jogos/tracking.
 * Copie este pacote {@code com.sanbot.debug} para outros apps Sanbot.
 */
public class DebugPose {

  public boolean playerPresent;
  public boolean fused;
  public boolean seated;
  public boolean nearMode;
  public boolean leftRaised;
  public boolean rightRaised;
  public boolean jumping;
  public boolean ducking;

  public float centroidX;
  public float centroidY;
  public int distanceZ;
  public int latencyMs;
  public float fps;
  public int leftArmPx;
  public int rightArmPx;
  public int people;
  public int pix;
  public float aspect;
  public int peakZ;

  public float headX;
  public float headY;
  public float neckX;
  public float neckY;
  public float spineX;
  public float spineY;
  public float lShoulderX;
  public float lShoulderY;
  public float lElbowX;
  public float lElbowY;
  public float lHandX;
  public float lHandY;
  public float rShoulderX;
  public float rShoulderY;
  public float rElbowX;
  public float rElbowY;
  public float rHandX;
  public float rHandY;
  public float lHipX;
  public float lHipY;
  public float rHipX;
  public float rHipY;
  public float lElev;
  public float rElev;

  public int yawCmd;
  public int pitchCmd;
  public int leftWingCmd;
  public int rightWingCmd;
  public int hwYaw;
  public int hwPitch;

  public String gesture = "IDLE";
  public String mode = "unknown";
  public long timestampMs;

  public void copyFrom(DebugPose s) {
    if (s == null) {
      return;
    }
    playerPresent = s.playerPresent;
    fused = s.fused;
    seated = s.seated;
    nearMode = s.nearMode;
    leftRaised = s.leftRaised;
    rightRaised = s.rightRaised;
    jumping = s.jumping;
    ducking = s.ducking;
    centroidX = s.centroidX;
    centroidY = s.centroidY;
    distanceZ = s.distanceZ;
    latencyMs = s.latencyMs;
    fps = s.fps;
    leftArmPx = s.leftArmPx;
    rightArmPx = s.rightArmPx;
    people = s.people;
    pix = s.pix;
    aspect = s.aspect;
    peakZ = s.peakZ;
    headX = s.headX;
    headY = s.headY;
    neckX = s.neckX;
    neckY = s.neckY;
    spineX = s.spineX;
    spineY = s.spineY;
    lShoulderX = s.lShoulderX;
    lShoulderY = s.lShoulderY;
    lElbowX = s.lElbowX;
    lElbowY = s.lElbowY;
    lHandX = s.lHandX;
    lHandY = s.lHandY;
    rShoulderX = s.rShoulderX;
    rShoulderY = s.rShoulderY;
    rElbowX = s.rElbowX;
    rElbowY = s.rElbowY;
    rHandX = s.rHandX;
    rHandY = s.rHandY;
    lHipX = s.lHipX;
    lHipY = s.lHipY;
    rHipX = s.rHipX;
    rHipY = s.rHipY;
    lElev = s.lElev;
    rElev = s.rElev;
    yawCmd = s.yawCmd;
    pitchCmd = s.pitchCmd;
    leftWingCmd = s.leftWingCmd;
    rightWingCmd = s.rightWingCmd;
    hwYaw = s.hwYaw;
    hwPitch = s.hwPitch;
    gesture = s.gesture;
    mode = s.mode;
    timestampMs = s.timestampMs;
  }

  public void appendJson(StringBuilder sb) {
    sb.append('{');
    sb.append("\"ts\":").append(timestampMs);
    sb.append(",\"player\":").append(playerPresent);
    sb.append(",\"fused\":").append(fused);
    sb.append(",\"seated\":").append(seated);
    sb.append(",\"near\":").append(nearMode);
    sb.append(",\"jump\":").append(jumping);
    sb.append(",\"duck\":").append(ducking);
    sb.append(",\"zMm\":").append(distanceZ);
    sb.append(",\"latMs\":").append(latencyMs);
    sb.append(",\"fps\":").append(fps);
    sb.append(",\"cx\":").append(centroidX);
    sb.append(",\"cy\":").append(centroidY);
    sb.append(",\"head\":[").append(headX).append(',').append(headY).append(']');
    sb.append(",\"lShoulder\":[").append(lShoulderX).append(',').append(lShoulderY).append(']');
    sb.append(",\"lElbow\":[").append(lElbowX).append(',').append(lElbowY).append(']');
    sb.append(",\"lHand\":[").append(lHandX).append(',').append(lHandY).append(']');
    sb.append(",\"rShoulder\":[").append(rShoulderX).append(',').append(rShoulderY).append(']');
    sb.append(",\"rElbow\":[").append(rElbowX).append(',').append(rElbowY).append(']');
    sb.append(",\"rHand\":[").append(rHandX).append(',').append(rHandY).append(']');
    sb.append(",\"lHip\":[").append(lHipX).append(',').append(lHipY).append(']');
    sb.append(",\"rHip\":[").append(rHipX).append(',').append(rHipY).append(']');
    sb.append(",\"lRaised\":").append(leftRaised);
    sb.append(",\"rRaised\":").append(rightRaised);
    sb.append(",\"lElev\":").append(lElev);
    sb.append(",\"rElev\":").append(rElev);
    sb.append(",\"lPx\":").append(leftArmPx);
    sb.append(",\"rPx\":").append(rightArmPx);
    sb.append(",\"people\":").append(people);
    sb.append(",\"pix\":").append(pix);
    sb.append(",\"aspect\":").append(aspect);
    sb.append(",\"peakZ\":").append(peakZ);
    sb.append(",\"motors\":{");
    sb.append("\"yawCmd\":").append(yawCmd);
    sb.append(",\"pitchCmd\":").append(pitchCmd);
    sb.append(",\"hwYaw\":").append(hwYaw);
    sb.append(",\"hwPitch\":").append(hwPitch);
    sb.append(",\"leftWing\":").append(leftWingCmd);
    sb.append(",\"rightWing\":").append(rightWingCmd);
    sb.append('}');
    sb.append(",\"gesture\":\"").append(escape(gesture)).append('"');
    sb.append(",\"mode\":\"").append(escape(mode)).append('"');
    sb.append('}');
  }

  private static String escape(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
