package com.felipe.elftemplate.debug;

import com.sanbot.debug.SanbotDebugHub;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.interfaces.hardware.GyroscopeListener;
import com.sanbot.opensdk.function.unit.interfaces.hardware.InfrareListener;
import com.sanbot.opensdk.function.unit.interfaces.hardware.ObstacleListener;
import com.sanbot.opensdk.function.unit.interfaces.hardware.PIRListener;
import com.sanbot.opensdk.function.unit.interfaces.hardware.TouchSensorListener;
import com.sanbot.opensdk.function.unit.interfaces.hardware.VoiceLocateListener;

/** Liga IR/gyro/PIR/toque do SDK ao {@link SanbotDebugHub}. Sem alloc no callback. */
public final class SanbotDebugSensors {

  private SanbotDebugSensors() {}

  public static void bind(HardWareManager hardware) {
    if (hardware == null) {
      return;
    }
    final SanbotDebugHub hub = SanbotDebugHub.get();
    hardware.setOnHareWareListener(
        new InfrareListener() {
          @Override
          public void infrareDistance(int part, int distance) {
            hub.onIr(part, distance);
          }
        });
    hardware.setOnHareWareListener(
        new GyroscopeListener() {
          @Override
          public void gyroscopeData(float driftAngle, float elevationAngle, float rollAngle) {
            hub.onGyro(driftAngle, elevationAngle, rollAngle);
          }

          @Override
          public void gyroscopeCheckResult(boolean isSuccess, boolean isCalibrated) {}
        });
    hardware.setOnHareWareListener(
        new PIRListener() {
          @Override
          public void onPIRCheckResult(boolean isChecked, int part) {
            hub.onPir(isChecked, part);
          }
        });
    hardware.setOnHareWareListener(
        new TouchSensorListener() {
          @Override
          public void onTouch(int part) {
            hub.onTouch(part);
          }

          @Override
          public void onTouch(int part, boolean pressed) {
            if (pressed) {
              hub.onTouch(part);
            }
          }
        });
    hardware.setOnHareWareListener(
        new VoiceLocateListener() {
          @Override
          public void voiceLocateResult(int angle) {
            hub.onVoice(angle);
          }
        });
    hardware.setOnHareWareListener(
        new ObstacleListener() {
          @Override
          public void onObstacleStatus(boolean status) {
            hub.onObstacle(status);
          }
        });
  }
}
