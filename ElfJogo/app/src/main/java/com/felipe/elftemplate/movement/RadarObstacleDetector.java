package com.felipe.elftemplate.movement;

import java.util.ArrayList;
import java.util.List;

/** Detector de obstáculos baseado nos 17 sensores infravermelhos perimetrais do Sanbot Elf. */
public class RadarObstacleDetector {

  private final int[] infraredDistances = new int[19];
  private final List<float[]> wallPoints = new ArrayList<>();

  public void updateInfrared(
      int part, int distance, double currentX, double currentY, double currentYawRad) {
    if (part >= 1 && part < infraredDistances.length) {
      infraredDistances[part] = distance;
      if (distance > 20 && distance < 100) {
        projectIrPointToMap(part, distance, currentX, currentY, currentYawRad);
      }
    }
  }

  private void projectIrPointToMap(int part, int distanceCm, double rx, double ry, double rYaw) {
    double angleOffset = (part - 9) * (Math.PI / 16.0);
    double totalAngle = rYaw + angleOffset;
    double distMeters = distanceCm / 100.0;

    float wx = (float) (rx + (distMeters * Math.cos(totalAngle)));
    float wy = (float) (ry + (distMeters * Math.sin(totalAngle)));

    synchronized (wallPoints) {
      if (wallPoints.size() > 500) {
        wallPoints.remove(0);
      }
      wallPoints.add(new float[] {wx, wy});
    }
  }

  public int[] getInfraredDistances() {
    return infraredDistances.clone();
  }

  public List<float[]> getWallPoints() {
    synchronized (wallPoints) {
      return new ArrayList<>(wallPoints);
    }
  }
}
