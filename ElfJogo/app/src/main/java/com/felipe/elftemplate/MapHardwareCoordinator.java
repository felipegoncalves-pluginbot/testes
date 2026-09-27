package com.felipe.elftemplate;

import android.content.Context;
import com.felipe.elftemplate.movement.AndroidLogger;
import com.felipe.elftemplate.movement.Logger;
import com.felipe.elftemplate.movement.SanbotMovementAdapter;
import com.felipe.elftemplate.movement.SensorFusionEngine;
import com.felipe.elftemplate.server.RobotWebServer;
import com.felipe.elftemplate.tracking.AstraDepthController;
import com.felipe.elftemplate.tracking.CameraController;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.SpeechManager;
import com.sanbot.opensdk.function.unit.WheelMotionManager;
import com.sanbot.opensdk.function.unit.interfaces.hardware.GyroscopeListener;
import com.sanbot.opensdk.function.unit.interfaces.hardware.InfrareListener;

/** Coordenador de hardware, sensores Astra/Sanbot e WebServer para a MapActivity. */
public class MapHardwareCoordinator {

  private final Context context;
  private final Logger logger = new AndroidLogger();
  private SensorFusionEngine sensorEngine;
  private RobotWebServer webServer;
  private CameraController cameraController;
  private AstraDepthController astraDepthController;
  private SanbotMovementAdapter movementAdapter;

  public MapHardwareCoordinator(Context context) {
    this.context = context;
    this.sensorEngine = new SensorFusionEngine(logger);
  }

  public void initHardware(
      WheelMotionManager wheel,
      HardWareManager hardware,
      HDCameraManager hdCamera,
      SpeechManager speech,
      RobotWebServer.RobotCommandListener commandListener,
      RobotWebServer.MapDataProvider dataProvider) {

    this.movementAdapter = new SanbotMovementAdapter(wheel);
    if (hardware != null) {
      hardware.setOnHareWareListener(
          new InfrareListener() {
            @Override
            public void infrareDistance(int part, int distance) {
              if (sensorEngine != null) sensorEngine.updateInfrared(part, distance);
            }
          });
      hardware.setOnHareWareListener(
          new GyroscopeListener() {
            @Override
            public void gyroscopeData(float driftAngle, float elevationAngle, float rollAngle) {
              if (sensorEngine != null) sensorEngine.updateCompass(driftAngle, 1.0);
            }

            @Override
            public void gyroscopeCheckResult(boolean isSuccess, boolean isCalibrated) {}
          });
    }

    if (hdCamera != null) {
      cameraController = new CameraController(hdCamera, 640, 480, (yuv, w, h) -> {});
      cameraController.start();
    }

    try {
      astraDepthController =
          new AstraDepthController(
              context,
              (depth, w, h) -> {
                if (sensorEngine != null)
                  sensorEngine.processAstraRgbd(null, depth, w, h, System.currentTimeMillis());
              });
      astraDepthController.start();
    } catch (Throwable ignored) {
    }

    try {
      webServer = new RobotWebServer(context, 8080, commandListener, dataProvider);
      webServer.start();
    } catch (Throwable ignored) {
    }
  }

  public SensorFusionEngine getSensorEngine() {
    return sensorEngine;
  }

  public SanbotMovementAdapter getMovementAdapter() {
    return movementAdapter;
  }

  public Logger getLogger() {
    return logger;
  }

  public void release() {
    if (webServer != null) {
      webServer.stop();
      webServer = null;
    }
    if (cameraController != null) {
      cameraController.stop();
      cameraController = null;
    }
    if (astraDepthController != null) {
      try {
        astraDepthController.stop();
      } catch (Throwable ignored) {
      }
      astraDepthController = null;
    }
  }
}
