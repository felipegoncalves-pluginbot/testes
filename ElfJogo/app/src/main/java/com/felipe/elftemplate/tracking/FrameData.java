package com.felipe.elftemplate.tracking;

public class FrameData {
  public byte[] pixelData;
  public int width;
  public int height;
  public long timestamp;

  public FrameData(byte[] pixelData, int width, int height, long timestamp) {
    this.pixelData = pixelData;
    this.width = width;
    this.height = height;
    this.timestamp = timestamp;
  }
}
