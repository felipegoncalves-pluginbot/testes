package com.sanbot.debug;

/**
 * Snapshot dos sensores do chassis Sanbot (IR, gyro, PIR, toque). Sem dependência de jogos.
 * Índices IR 1–17 conforme SDK (13 = peito).
 */
public final class DebugSensors {

  public static final int IR_COUNT = 18;

  public final int[] irCm = new int[IR_COUNT];
  public float gyroYaw;
  public float gyroPitch;
  public float gyroRoll;
  public boolean pirFront;
  public boolean pirRear;
  public boolean obstacle;
  public int touchPart;
  public int voiceDeg = -1;
  public long gyroMs;
  public long irMs;
  public long pirMs;
  public long touchMs;
  public long rgbMs;

  public void setIr(int part, int cm) {
    if (part > 0 && part < IR_COUNT) {
      irCm[part] = cm;
      irMs = System.currentTimeMillis();
    }
  }

  public void copyFrom(DebugSensors s) {
    if (s == null) {
      return;
    }
    System.arraycopy(s.irCm, 0, irCm, 0, IR_COUNT);
    gyroYaw = s.gyroYaw;
    gyroPitch = s.gyroPitch;
    gyroRoll = s.gyroRoll;
    pirFront = s.pirFront;
    pirRear = s.pirRear;
    obstacle = s.obstacle;
    touchPart = s.touchPart;
    voiceDeg = s.voiceDeg;
    gyroMs = s.gyroMs;
    irMs = s.irMs;
    pirMs = s.pirMs;
    touchMs = s.touchMs;
    rgbMs = s.rgbMs;
  }

  public void appendJson(StringBuilder sb) {
    sb.append('{');
    sb.append("\"gyro\":[")
        .append(gyroYaw)
        .append(',')
        .append(gyroPitch)
        .append(',')
        .append(gyroRoll)
        .append(']');
    sb.append(",\"chest\":").append(irCm[13]);
    sb.append(",\"torsoL\":").append(irCm[11]);
    sb.append(",\"torsoR\":").append(irCm[12]);
    sb.append(",\"wingL\":").append(irCm[16]);
    sb.append(",\"wingR\":").append(irCm[14]);
    sb.append(",\"pirF\":").append(pirFront);
    sb.append(",\"pirR\":").append(pirRear);
    sb.append(",\"bump\":").append(obstacle);
    sb.append(",\"touch\":").append(touchPart);
    sb.append(",\"voiceDeg\":").append(voiceDeg);
    sb.append(",\"ir\":[");
    for (int i = 0; i < IR_COUNT; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(irCm[i]);
    }
    sb.append(']');
    sb.append(",\"rgbMs\":").append(rgbMs);
    sb.append('}');
  }
}
