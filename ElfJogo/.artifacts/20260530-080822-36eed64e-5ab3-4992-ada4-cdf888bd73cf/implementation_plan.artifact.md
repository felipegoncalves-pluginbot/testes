# Upgrading to 3D vSLAM, Camera Preview, and EKF Stability (v20)

The robot experiences coordinate drift ($x,y$ rising) while stationary and the user wants to better utilize the 3D camera and see the robot's visual feed.

## User Review Required

- **Camera Preview Performance:** Streaming JPEG frames over the `RobotWebServer` might consume CPU/bandwidth. I'll limit the rate to 1-2 FPS for monitoring.
- **Astra Depth Access:** The Sanbot OpenSDK might not expose Depth frames. I will attempt to locate the internal USB device for the Orbbec Astra.

## Proposed Changes

### Localization & Sensor Fusion (Robustness)

#### [ExtendedKalmanFilter.java](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/movement/ExtendedKalmanFilter.java)

- Add `resetVelocity()` method to zero out `x[3]` (vx), `x[4]` (vy), and `x[5]` (omega).
- Increase Process Noise $Q$ for velocity states when moving, and decrease it significantly when stopped.

#### [SensorFusionEngine.java](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/movement/SensorFusionEngine.java)

- Call `ekf.resetVelocity()` when `isPhysicallyStopped` is true.
- Ignore Visual/WiFi updates that would cause movement if the robot is definitively stopped.
- Add logs for ALL raw sensors (IR, Gyro, WiFi, Vision).

---

### Visual SLAM (3D and Monitoring)

#### [RobotWebServer.java](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/web/RobotWebServer.java)

- Add a new route `/camera_preview` that serves the latest captured frame as JPEG.

#### [MapActivity.java](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/java/com/felipe/elftemplate/MapActivity.java)

- Capture the YUV frame from the camera stream, convert to Bitmap/JPEG, and provide it to the WebServer.
- Add a "Show Camera" button to the internal dashboard.

#### [native-lib.cpp](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/cpp/native-lib.cpp)

- [Researching] Check if `AVFrame` from OpenSDK contains depth data or if it's strictly color.

---

### Dashboard (User Interface)

#### [index.html](file:///home/pluginbot/Documentos/AndroidStudio/Elf/ElfJogo/app/src/main/assets/www/index.html)

- Add a camera preview window and a button to toggle it.
- Add real-time sensor graphs (miniature) for IR and Gyro.

## Verification Plan

### Automated Tests
- N/A (Hardware dependent)

### Manual Verification
1.  **Stationary Test:** Observe $x,y$ coordinates for 1 minute while the robot is stopped. They must remain exactly constant.
2.  **Camera Feed:** Open the dashboard and verify if the "Show Camera" button displays the robot's live view.
3.  **Sensor Audit:** Check logcat for `SENSOR_AUDIT` tags to verify all values are within expected ranges.
