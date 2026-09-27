package com.felipe.elftemplate.logic;

/**
 * Extended Kalman Filter (Simplified) para fundir Odometria Visual (RTAB-Map) e Bússola
 * (Magnetômetro).
 */
public class RobotEKF {

  // Estado: [x, y, yaw]
  private float[] state = new float[] {0f, 0f, 0f};

  // Covariância do estado (simplificada)
  private float[] covariance = new float[] {0.1f, 0.1f, 0.1f};

  public RobotEKF() {}

  public float[] getState() {
    return state;
  }

  /** Atualiza o estado via Odometria Visual. */
  public void updateVisualOdometry(float x, float y, float yaw, double cov) {
    // Implementação simplificada de fusão EKF baseada em ganho de Kalman
    // Na vida real, aplicamos as matrizes H e K.
    float kX = (float) (covariance[0] / (covariance[0] + cov));
    float kY = (float) (covariance[1] / (covariance[1] + cov));
    float kYaw = (float) (covariance[2] / (covariance[2] + cov));

    state[0] = state[0] + kX * (x - state[0]);
    state[1] = state[1] + kY * (y - state[1]);

    // Cuidado com o wrap do angulo (Pi / -Pi), simplificado aqui:
    float diffYaw = yaw - state[2];
    state[2] = state[2] + kYaw * diffYaw;

    covariance[0] = (1 - kX) * covariance[0];
    covariance[1] = (1 - kY) * covariance[1];
    covariance[2] = (1 - kYaw) * covariance[2];
  }

  /** Atualiza o Yaw via Bússola. */
  public void updateCompass(float yaw, double cov) {
    // Se a covariância for absurdamente alta (outlier de distorção), ignora.
    if (cov > 100.0) {
      return;
    }

    float kYaw = (float) (covariance[2] / (covariance[2] + cov));

    float diffYaw = yaw - state[2];

    // Wrap angle difference between -PI and PI
    while (diffYaw > Math.PI) diffYaw -= 2 * Math.PI;
    while (diffYaw < -Math.PI) diffYaw += 2 * Math.PI;

    state[2] = state[2] + kYaw * diffYaw;
    covariance[2] = (1 - kYaw) * covariance[2];
  }

  public void predict(double dt, double cmdV, double cmdW) {
    state[2] += (float) (cmdW * dt);
    state[0] += (float) (cmdV * Math.cos(state[2]) * dt);
    state[1] += (float) (cmdV * Math.sin(state[2]) * dt);

    covariance[0] += 0.05f * dt;
    covariance[1] += 0.05f * dt;
    covariance[2] += 0.05f * dt;
  }

  public void correctStationary() {
    covariance[0] = Math.max(0.01f, covariance[0] * 0.9f);
    covariance[1] = Math.max(0.01f, covariance[1] * 0.9f);
    covariance[2] = Math.max(0.01f, covariance[2] * 0.9f);
  }

  public double getX() {
    return state[0];
  }

  public double getY() {
    return state[1];
  }

  public double getYaw() {
    return state[2];
  }
}
