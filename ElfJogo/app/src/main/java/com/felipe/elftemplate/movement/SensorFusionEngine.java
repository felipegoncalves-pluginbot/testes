package com.felipe.elftemplate.movement;

import com.felipe.elftemplate.logic.RobotEKF;
import com.felipe.elftemplate.tracking.RtabmapWrapper;
import java.util.ArrayList;
import java.util.List;

/** Fachada leve de Fusão de Sensores e Odometria para o Sanbot Elf. */
public class SensorFusionEngine {

  private static final String TAG = "SensorFusion";

  private final Object dataLock = new Object();
  private final Logger logger;
  private final RtabmapWrapper rtabmap;
  private final RobotEKF ekf;
  private final RadarObstacleDetector obstacleDetector = new RadarObstacleDetector();
  private final List<float[]> pathHistory = new ArrayList<>();

  private double x = 0.0;
  private double y = 0.0;
  private double currentYawRad = 0.0;
  private double currentConfidence = 1.0;
  private String currentWheelCommand = "stop";
  private boolean isPhysicallyStopped = true;

  public SensorFusionEngine(Logger logger) {
    this.logger = logger;
    this.ekf = new RobotEKF();
    this.rtabmap = new RtabmapWrapper();
    this.rtabmap.init();
    pathHistory.add(new float[] {0, 0});
  }

  public void updateCompass(float yawDegrees, double covariance) {
    synchronized (dataLock) {
      float yawRads = (float) Math.toRadians(yawDegrees);
      ekf.updateCompass(yawRads, covariance);
      syncPoseFromEKF();
    }
  }

  public void processAstraRgbd(byte[] rgb, short[] depth, int width, int height, long timestamp) {
    if (isPhysicallyStopped && "stop".equals(currentWheelCommand)) {
      return;
    }

    float[] voResult = rtabmap.processRgbd(rgb, depth, width, height, timestamp);
    if (voResult != null && voResult.length >= 4) {
      synchronized (dataLock) {
        float voX = voResult[0];
        float voY = voResult[1];
        float voYaw = voResult[2];
        float conf = voResult[3];
        double covVO = (conf > 0) ? (1.0 / conf) : 10.0;

        ekf.updateVisualOdometry(voX, voY, voYaw, covVO);
        syncPoseFromEKF();
        this.currentConfidence = conf;
        recordPathInternal();
      }
    }
  }

  public void updateInfrared(int part, int distance) {
    obstacleDetector.updateInfrared(part, distance, x, y, currentYawRad);
  }

  public void setWheelState(String command, boolean isStopped) {
    synchronized (dataLock) {
      this.currentWheelCommand = command;
      this.isPhysicallyStopped = isStopped;
    }
  }

  public void setWheelCommand(String command) {
    setWheelState(command, "stop".equals(command));
  }

  public boolean isPathBlocked(double thresholdMeters) {
    int[] distances = obstacleDetector.getInfraredDistances();
    for (int d : distances) {
      if (d > 0 && (d / 100.0) < thresholdMeters) {
        return true;
      }
    }
    return false;
  }

  public void reset() {
    synchronized (dataLock) {
      this.x = 0.0;
      this.y = 0.0;
      this.currentYawRad = 0.0;
      this.currentConfidence = 1.0;
      this.currentWheelCommand = "stop";
      this.isPhysicallyStopped = true;
      this.pathHistory.clear();
      this.pathHistory.add(new float[] {0, 0});
    }
  }

  public String getWheelCommand() {
    synchronized (dataLock) {
      return currentWheelCommand;
    }
  }

  public void processWheelOdometry() {}

  private void syncPoseFromEKF() {
    this.x = ekf.getX();
    this.y = ekf.getY();
    this.currentYawRad = ekf.getYaw();
  }

  private void recordPathInternal() {
    if (pathHistory.isEmpty()) {
      pathHistory.add(new float[] {(float) x, (float) y});
      return;
    }
    float[] last = pathHistory.get(pathHistory.size() - 1);
    float dx = (float) x - last[0];
    float dy = (float) y - last[1];
    if ((dx * dx + dy * dy) > 0.0025f) {
      pathHistory.add(new float[] {(float) x, (float) y});
    }
  }

  public double getX() {
    synchronized (dataLock) {
      return x;
    }
  }

  public double getY() {
    synchronized (dataLock) {
      return y;
    }
  }

  public double getYawRad() {
    synchronized (dataLock) {
      return currentYawRad;
    }
  }

  public double getConfidence() {
    synchronized (dataLock) {
      return currentConfidence;
    }
  }

  public List<float[]> getPathHistory() {
    synchronized (dataLock) {
      return new ArrayList<>(pathHistory);
    }
  }

  public List<float[]> getWallPoints() {
    return obstacleDetector.getWallPoints();
  }

  public List<float[]> getKnownPoints() {
    return new ArrayList<>();
  }

  public List<RadarObject> getRadarObjects() {
    return new ArrayList<>();
  }

  public int[] getInfraredDistances() {
    return obstacleDetector.getInfraredDistances();
  }
}
