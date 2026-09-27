package com.felipe.elftemplate.tracking;

public class GyroscopeData {
  public float pitch;
  public float roll;
  public float yaw;
  public long timestamp;

  public GyroscopeData(float pitch, float roll, float yaw, long timestamp) {
    this.pitch = pitch;
    this.roll = roll;
    this.yaw = yaw;
    this.timestamp = timestamp;
  }
}
