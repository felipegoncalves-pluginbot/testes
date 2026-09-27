package com.felipe.elftemplate.tracking;

/** Blob humano detectado no mapa de profundidade (equivalente a um player index Kinect). */
public class PersonBlob {

  public final int label;
  public final int minX;
  public final int maxX;
  public final int minY;
  public final int maxY;
  public final float centroidX;
  public final float centroidY;
  public final int distanceZ;
  public final int pixelCount;
  public final float aspectHW;
  public final float fillDensity;
  public final boolean nearMode;
  public final boolean seatedPose;

  public PersonBlob(
      int label,
      int minX,
      int maxX,
      int minY,
      int maxY,
      float centroidX,
      float centroidY,
      int distanceZ,
      int pixelCount,
      float aspectHW,
      float fillDensity,
      boolean nearMode,
      boolean seatedPose) {
    this.label = label;
    this.minX = minX;
    this.maxX = maxX;
    this.minY = minY;
    this.maxY = maxY;
    this.centroidX = centroidX;
    this.centroidY = centroidY;
    this.distanceZ = distanceZ;
    this.pixelCount = pixelCount;
    this.aspectHW = aspectHW;
    this.fillDensity = fillDensity;
    this.nearMode = nearMode;
    this.seatedPose = seatedPose;
  }
}
