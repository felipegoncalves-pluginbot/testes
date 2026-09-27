package com.felipe.elftemplate;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import com.felipe.elftemplate.movement.JoystickMovementController;
import com.felipe.elftemplate.movement.MapView;
import com.felipe.elftemplate.movement.NavigationController;
import com.felipe.elftemplate.movement.RadarObject;
import com.felipe.elftemplate.movement.SensorFusionEngine;
import com.felipe.elftemplate.movement.Waypoint;
import com.felipe.elftemplate.server.RobotWebServer;
import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.HardWareManager;
import com.sanbot.opensdk.function.unit.SpeechManager;
import com.sanbot.opensdk.function.unit.WheelMotionManager;
import java.util.ArrayList;
import java.util.List;

/** Tela de Mapeamento, SLAM, Waypoints e Teleoperação WASD do Sanbot Elf. */
public class MapActivity extends BindBaseActivity {

  private MapHardwareCoordinator hardwareCoordinator;
  private NavigationController navController;
  private JoystickMovementController joystickController;
  private MapView mapView;
  private TextView tvCoordinates;
  private TextView tvServerInfo;

  private final Handler updateHandler = new Handler(Looper.getMainLooper());
  private final List<Waypoint> waypoints = new ArrayList<>();
  private final Runnable loopRunnable =
      new Runnable() {
        @Override
        public void run() {
          updateUiLoop();
          updateHandler.postDelayed(this, 100);
        }
      };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    register(MapActivity.class);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_map);

    mapView = findViewById(R.id.map_view);
    tvCoordinates = findViewById(R.id.tv_coordinates);
    tvServerInfo = findViewById(R.id.tv_server_info);
    hardwareCoordinator = new MapHardwareCoordinator(this);

    setupButtons();
  }

  @Override
  protected void onMainServiceConnected() {
    WheelMotionManager wheel =
        (WheelMotionManager) getUnitManager(FuncConstant.WHEELMOTION_MANAGER);
    HardWareManager hardware = (HardWareManager) getUnitManager(FuncConstant.HARDWARE_MANAGER);
    HDCameraManager hdCamera = (HDCameraManager) getUnitManager(FuncConstant.HDCAMERA_MANAGER);
    SpeechManager speech = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);

    hardwareCoordinator.initHardware(
        wheel,
        hardware,
        hdCamera,
        speech,
        new RobotWebServer.RobotCommandListener() {
          @Override
          public void onCommandReceived(String command) {
            if (joystickController != null) joystickController.handleCommand(command);
          }

          @Override
          public void onActionReceived(String action) {}
        },
        createMapDataProvider());

    SensorFusionEngine engine = hardwareCoordinator.getSensorEngine();
    navController =
        new NavigationController(
            hardwareCoordinator.getMovementAdapter(), engine, hardwareCoordinator.getLogger());
    joystickController =
        new JoystickMovementController(
            hardwareCoordinator.getMovementAdapter(), engine, hardwareCoordinator.getLogger());

    if (tvServerInfo != null) {
      tvServerInfo.setText("Servidor Web Ativo na porta 8080");
    }
    updateHandler.post(loopRunnable);
  }

  private void setupButtons() {
    Button btnSaveTarget = findViewById(R.id.btn_save_target);
    if (btnSaveTarget != null) {
      btnSaveTarget.setOnClickListener(
          v -> {
            SensorFusionEngine engine = hardwareCoordinator.getSensorEngine();
            if (engine != null) {
              Waypoint wp =
                  new Waypoint("Alvo " + (waypoints.size() + 1), engine.getX(), engine.getY());
              waypoints.add(wp);
              mapView.setWaypoints(waypoints);
            }
          });
    }

    Button btnBack = findViewById(R.id.btn_back);
    if (btnBack != null) {
      btnBack.setOnClickListener(v -> finish());
    }
  }

  private void updateUiLoop() {
    SensorFusionEngine engine = hardwareCoordinator.getSensorEngine();
    if (engine != null && mapView != null) {
      float x = (float) engine.getX();
      float y = (float) engine.getY();
      float yaw = (float) engine.getYawRad();
      mapView.updateRobotPose(x, y, yaw);
      mapView.setPathHistory(engine.getPathHistory());
      mapView.setWallPoints(engine.getWallPoints());
      if (tvCoordinates != null) {
        tvCoordinates.setText(
            String.format("X: %.2fm | Y: %.2fm | Yaw: %d°", x, y, (int) Math.toDegrees(yaw)));
      }
    }
  }

  private RobotWebServer.MapDataProvider createMapDataProvider() {
    return new RobotWebServer.MapDataProvider() {
      @Override
      public double getX() {
        return hardwareCoordinator.getSensorEngine().getX();
      }

      @Override
      public double getY() {
        return hardwareCoordinator.getSensorEngine().getY();
      }

      @Override
      public double getYaw() {
        return hardwareCoordinator.getSensorEngine().getYawRad();
      }

      @Override
      public boolean isRecording() {
        return false;
      }

      @Override
      public boolean isNavigating() {
        return navController != null && navController.isNavigating();
      }

      @Override
      public List<Waypoint> getWaypoints() {
        return new ArrayList<>(waypoints);
      }

      @Override
      public List<float[]> getPath() {
        return hardwareCoordinator.getSensorEngine().getPathHistory();
      }

      @Override
      public List<float[]> getKnownPoints() {
        return new ArrayList<>();
      }

      @Override
      public List<float[]> getWallPoints() {
        return hardwareCoordinator.getSensorEngine().getWallPoints();
      }

      @Override
      public List<RadarObject> getRadarObjects() {
        return new ArrayList<>();
      }

      @Override
      public int[] getInfrared() {
        return hardwareCoordinator.getSensorEngine().getInfraredDistances();
      }

      @Override
      public double getRawGyro() {
        return 0;
      }

      @Override
      public float[] getAccel() {
        return new float[3];
      }

      @Override
      public byte[] getLatestFrame() {
        return null;
      }

      @Override
      public byte[] getOccupancyGrid() {
        return null;
      }

      @Override
      public int getAstraFrames() {
        return 0;
      }

      @Override
      public boolean isAstraActive() {
        return true;
      }

      @Override
      public boolean isStopped() {
        return true;
      }
    };
  }

  @Override
  protected void onDestroy() {
    updateHandler.removeCallbacks(loopRunnable);
    if (navController != null) navController.stopNavigation();
    if (hardwareCoordinator != null) hardwareCoordinator.release();
    super.onDestroy();
  }
}
