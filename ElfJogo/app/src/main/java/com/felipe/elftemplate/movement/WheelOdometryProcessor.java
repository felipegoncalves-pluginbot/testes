package com.felipe.elftemplate.movement;

/** Processador de odometria de rodas para integração cinemática de deslocamento e orientação. */
public class WheelOdometryProcessor {

  private double estimatedX = 0.0;
  private double estimatedY = 0.0;
  private double estimatedHeading = 0.0;

  public void reset(double initialX, double initialY, double initialHeading) {
    this.estimatedX = initialX;
    this.estimatedY = initialY;
    this.estimatedHeading = initialHeading;
  }

  public void updateWithWheelDisplacement(double displacementMeters, double deltaHeadingRad) {
    this.estimatedHeading += deltaHeadingRad;
    this.estimatedX += displacementMeters * Math.cos(estimatedHeading);
    this.estimatedY += displacementMeters * Math.sin(estimatedHeading);
  }

  public double getEstimatedX() {
    return estimatedX;
  }

  public double getEstimatedY() {
    return estimatedY;
  }

  public double getEstimatedHeading() {
    return estimatedHeading;
  }
}
