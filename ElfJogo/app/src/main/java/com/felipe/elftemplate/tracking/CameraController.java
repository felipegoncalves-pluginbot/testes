package com.felipe.elftemplate.tracking;

import android.util.Log;
import com.sanbot.opensdk.beans.OperationResult;
import com.sanbot.opensdk.function.beans.StreamOption;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.interfaces.media.MediaStreamListener;

/**
 * Câmera HD Sanbot: H.264 → MediaCodec → NV21. Perfis espelhados do Elf_Controle
 * (MAIN_STREAM 720p) e pose (SUB_STREAM 480p).
 */
public class CameraController implements VisionMediaDecoder.FrameCallback {

  private static final String TAG = "CameraController";

  public enum StreamProfile {
    /** MoveNet, espelho e offload aux — SUB_STREAM 640×480 4:3 (menor latência que MAIN 720p). */
    POSE_EFFICIENT(640, 480, StreamOption.SUB_STREAM),
    /** Preview legado — MAIN_STREAM 720p (mais CPU/latência no decode H.264). */
    PREVIEW_HD(1280, 720, StreamOption.MAIN_STREAM);

    public final int width;
    public final int height;
    public final int channel;

    StreamProfile(int width, int height, int channel) {
      this.width = width;
      this.height = height;
      this.channel = channel;
    }
  }

  private HDCameraManager hdCameraManager;
  private VisionMediaDecoder visionMediaDecoder;
  private int mediaHandle = -1;
  private final CameraFrameListener listener;
  private final StreamProfile profile;
  private byte[] nv21Buffer;
  private volatile boolean active;

  public interface CameraFrameListener {
    void onFrameReceived(byte[] nv21Data, int width, int height);
  }

  public CameraController(
      HDCameraManager manager, StreamProfile profile, CameraFrameListener listener) {
    this.hdCameraManager = manager;
    this.profile = profile;
    this.listener = listener;
  }

  public CameraController(
      HDCameraManager manager, int width, int height, CameraFrameListener listener) {
    this(
        manager,
        width == StreamProfile.PREVIEW_HD.width && height == StreamProfile.PREVIEW_HD.height
            ? StreamProfile.PREVIEW_HD
            : StreamProfile.POSE_EFFICIENT,
        listener);
  }

  public void start() {
    if (mediaHandle != -1) {
      return;
    }
    active = true;
    Log.d(
        TAG,
        "Iniciando CameraController "
            + profile.width
            + "x"
            + profile.height
            + " ch="
            + profile.channel);

    visionMediaDecoder =
        new VisionMediaDecoder(profile.width, profile.height, this);
    visionMediaDecoder.start();

    StreamOption option = new StreamOption();
    option.setChannel(profile.channel);
    option.setDecodType(StreamOption.HARDWARE_DECODE);
    option.setJustIframe(false);

    hdCameraManager.setMediaListener(
        new MediaStreamListener() {
          @Override
          public void getVideoStream(int length, byte[] data, int width, int height) {
            if (visionMediaDecoder != null && data != null) {
              visionMediaDecoder.feedData(data);
            }
          }

          @Override
          public void getAudioStream(int length, byte[] data) {}
        });

    OperationResult result = hdCameraManager.openStream(option);
    if (result != null && result.getErrorCode() == 1) {
      try {
        mediaHandle = Integer.parseInt(result.getResult());
        DebugTrace.logImmediate(
            "A",
            "CameraController.start",
            "camera_stream_ok",
            "{\"handle\":"
                + mediaHandle
                + ",\"w\":"
                + profile.width
                + ",\"h\":"
                + profile.height
                + "}");
      } catch (NumberFormatException e) {
        Log.e(TAG, "Erro ao converter handle: " + result.getResult());
      }
    } else {
      Log.e(TAG, "Falha ao abrir stream: " + (result != null ? result.getErrorCode() : "null"));
    }
  }

  public void stop() {
    active = false;
    if (mediaHandle != -1) {
      hdCameraManager.closeStream(mediaHandle);
      mediaHandle = -1;
    }
    if (visionMediaDecoder != null) {
      visionMediaDecoder.stopDecoder();
      visionMediaDecoder = null;
    }
    nv21Buffer = null;
  }

  @Override
  public void onFrameDecoded(byte[] yuvData, int length, int width, int height) {
    if (!active || listener == null || yuvData == null) {
      return;
    }
    int frameBytes = width * height * 3 / 2;
    if (nv21Buffer == null || nv21Buffer.length < frameBytes) {
      nv21Buffer = new byte[frameBytes];
    }
    Yuv420pConverter.toNv21(yuvData, nv21Buffer, width, height);
    listener.onFrameReceived(nv21Buffer, width, height);
  }
}
