package com.felipe.elftemplate.tracking;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.openni.Device;
import org.openni.DeviceInfo;
import org.openni.OpenNI;
import org.openni.PixelFormat;
import org.openni.SensorType;
import org.openni.VideoFrameRef;
import org.openni.VideoMode;
import org.openni.VideoStream;
import org.openni.android.OpenNIHelper;

/**
 * Controlador para a Câmera de Profundidade Orbbec Astra via OpenNI2. Emite telemetria de FPS e
 * integridade de frames estruturada sob a tag [ASTRA-STREAM].
 */
public class AstraDepthController implements OpenNIHelper.DeviceOpenListener {
  private static final String TAG = "AstraDepth";

  static {
    try {
      System.loadLibrary("OpenNI2");
      Log.i(TAG, "[ASTRA-STREAM] Native OpenNI2 library loaded successfully.");
    } catch (UnsatisfiedLinkError e) {
      Log.e(TAG, "[ASTRA-STREAM] CRITICAL: Could not load libOpenNI2.so: " + e.getMessage());
    }
  }

  public interface DepthFrameListener {
    void onDepthFrameReceived(short[] depthData, int width, int height);
  }

  private final Context context;
  private final DepthFrameListener listener;

  private OpenNIHelper openNIHelper;
  private Device device;
  private VideoStream depthStream;
  private boolean isRunning = false;
  private short[] depthBuffer;

  // Telemetria de FPS
  private long frameCount = 0;
  private long lastFpsLogTime = 0;
  private float currentFps = 0.0f;

  public AstraDepthController(Context context, DepthFrameListener listener) {
    this.context = context;
    this.listener = listener;
  }

  public void start() {
    if (isRunning) return;

    Log.i(TAG, "[ASTRA-STREAM] Solicitando inicialização do dispositivo Astra via OpenNIHelper...");
    try {
      if (openNIHelper == null) {
        openNIHelper = new OpenNIHelper(context);
      }
      openNIHelper.requestDeviceOpen(this);
    } catch (Exception e) {
      Log.e(TAG, "[ASTRA-STREAM] Erro ao solicitar abertura via Helper: " + e.getMessage(), e);
    }
  }

  @Override
  public void onDeviceOpened(UsbDevice usbDevice) {
    Log.i(TAG, "[ASTRA-STREAM] USB Device Aberto com sucesso: " + usbDevice.getDeviceName());

    try {
      OpenNI.setLogAndroidOutput(true);
      OpenNI.initialize();

      List<DeviceInfo> devices = OpenNI.enumerateDevices();
      if (devices.isEmpty()) {
        Log.e(TAG, "[ASTRA-STREAM] OpenNI não detectou câmeras após permissão USB.");
        return;
      }

      Log.i(TAG, "[ASTRA-STREAM] Câmeras Astra detectadas: " + devices.size() + " dispositivo(s).");
      for (DeviceInfo info : devices) {
        Log.i(
            TAG,
            String.format(
                "[ASTRA-STREAM] Device: Name=%s, URI=%s, VendorId=%d, ProductId=%d",
                info.getName(), info.getUri(), info.getUsbVendorId(), info.getUsbProductId()));
      }

      device = Device.open();
      depthStream = VideoStream.create(device, SensorType.DEPTH);

      // Configuração 640x480 @ 30fps
      VideoMode vm = new VideoMode();
      vm.setResolution(640, 480);
      vm.setFps(30);
      vm.setPixelFormat(PixelFormat.DEPTH_1_MM);
      depthStream.setVideoMode(vm);

      depthStream.addNewFrameListener(
          new VideoStream.NewFrameListener() {
            @Override
            public void onFrameReady(VideoStream stream) {
              VideoFrameRef frame = stream.readFrame();
              if (frame != null) {
                processFrame(frame);
                frame.release();
              }
            }
          });

      depthStream.start();
      isRunning = true;
      lastFpsLogTime = System.currentTimeMillis();
      Log.i(TAG, "[ASTRA-STREAM] Stream de Profundidade INICIADO (640x480 @ 30fps)!");

    } catch (Exception e) {
      Log.e(TAG, "[ASTRA-STREAM] Erro ao configurar stream Astra: " + e.getMessage(), e);
    }
  }

  @Override
  public void onDeviceOpenFailed(String msg) {
    Log.e(TAG, "[ASTRA-STREAM] Falha ao abrir dispositivo USB: " + msg);
  }

  @Override
  public void onDeviceNotFound() {
    Log.e(TAG, "[ASTRA-STREAM] Nenhum dispositivo Orbbec Astra encontrado no USB.");
  }

  private void processFrame(VideoFrameRef frame) {
    ByteBuffer buffer = frame.getData().order(ByteOrder.LITTLE_ENDIAN);
    int width = frame.getVideoMode().getResolutionX();
    int height = frame.getVideoMode().getResolutionY();

    int size = width * height;
    if (depthBuffer == null || depthBuffer.length != size) {
      depthBuffer = new short[size];
    }
    buffer.asShortBuffer().get(depthBuffer);

    frameCount++;
    long now = System.currentTimeMillis();
    long elapsed = now - lastFpsLogTime;

    if (elapsed >= 1000) {
      currentFps = (frameCount * 1000.0f) / elapsed;
      Log.i(
          TAG,
          String.format(
              "[ASTRA-STREAM] Telemetria: FPS=%.1f | Resolução=%dx%d | Frames=%d",
              currentFps, width, height, frameCount));
      frameCount = 0;
      lastFpsLogTime = now;
    }

    if (listener != null) {
      listener.onDepthFrameReceived(depthBuffer, width, height);
    }
  }

  public float getCurrentFps() {
    return currentFps;
  }

  public void stop() {
    Log.i(TAG, "[ASTRA-STREAM] Encerrando stream Astra Cam...");
    isRunning = false;
    if (depthStream != null) {
      depthStream.stop();
      depthStream.destroy();
      depthStream = null;
    }
    if (device != null) {
      device.close();
      device = null;
    }
    if (openNIHelper != null) {
      openNIHelper.shutdown();
      openNIHelper = null;
    }
  }
}
