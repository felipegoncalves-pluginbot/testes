package com.felipe.elftemplate.mirror;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import com.felipe.elftemplate.tracking.ArmElevationMapper;
import com.felipe.elftemplate.tracking.CameraController;
import com.felipe.elftemplate.tracking.RgbPreviewBridge;
import com.felipe.elftemplate.tracking.TrackingProvider;
import com.felipe.elftemplate.tracking.TrackingResult;
import org.junit.Before;
import org.junit.Test;

/** Valida orquestração espelho sem hardware Sanbot nem Kinect real. */
public class MirrorSessionControllerTest {

  private FakeTrackingProvider provider;
  private MirrorSessionController controller;
  private int lastLeftWing = -1;
  private int lastRightWing = -1;
  private int overlayFrames;
  private int lastHeadYaw = Integer.MIN_VALUE;
  private int lastHeadPitch = Integer.MIN_VALUE;

  @Before
  public void setUp() {
    provider = new FakeTrackingProvider();
    controller = new MirrorSessionController(provider);
    controller.setOverlayListener((result, silhouette) -> overlayFrames++);
    controller.setRobotFeedback(
        new StubMirrorFeedback() {
          @Override
          public void setMirrorWingAngles(int leftAngle, int rightAngle) {
            lastLeftWing = leftAngle;
            lastRightWing = rightAngle;
          }

          @Override
          public void setMirrorHead2D(int targetYawOffset, int targetPitchOffset) {
            lastHeadYaw = targetYawOffset;
            lastHeadPitch = targetPitchOffset;
          }
        });
    controller.start(null, CameraController.StreamProfile.PREVIEW_HD);
  }

  @Test
  public void forwardsPlayerElevationToProportionalWingAngles() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.leftHandElevation = 0.55f;
    result.rightHandElevation = 0.1f;

    provider.deliver(result, null);

    assertEquals(ArmElevationMapper.elevationToWingAngle(0.55f), lastLeftWing);
    assertEquals(ArmElevationMapper.elevationToWingAngle(0.1f), lastRightWing);
  }

  @Test
  public void clearsMotorsAndCentersHeadWhenPlayerLost() {
    TrackingResult present = new TrackingResult();
    present.isPlayerPresent = true;
    present.leftHandElevation = 0.6f;
    provider.deliver(present, null);
    assertTrue(lastLeftWing > ArmElevationMapper.WING_ANGLE_MIN);

    TrackingResult absent = new TrackingResult();
    absent.isPlayerPresent = false;
    provider.deliver(absent, null);

    assertEquals(ArmElevationMapper.WING_ANGLE_MIN, lastLeftWing);
    assertEquals(ArmElevationMapper.WING_ANGLE_MIN, lastRightWing);
    assertEquals("cabeça volta ao centro", 0, lastHeadYaw);
    assertEquals("overlay recebe todo frame", 2, overlayFrames);
  }

  /** O Astra está na cabeça: inclinar a cabeça desloca o plano do chão e perde o jogador. */
  @Test
  public void neverTiltsTheHeadEvenWhenThePlayerHeadIsHighInFrame() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.head.set(0.20f, 0.05f, 2000);

    provider.deliver(result, null);

    assertEquals("pitch nunca é comandado", 0, lastHeadPitch);
    assertTrue("yaw ainda acompanha o jogador à esquerda da imagem", lastHeadYaw > 0);
  }

  @Test
  public void stopPreventsFurtherMotorUpdates() {
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.leftHandElevation = 0.5f;
    provider.deliver(result, null);
    int angleAfterFirst = lastLeftWing;

    controller.stop();
    result.leftHandElevation = 0.9f;
    provider.deliver(result, null);

    assertEquals(angleAfterFirst, lastLeftWing);
  }

  @Test
  public void exposesLocalKinectCapabilityFromProvider() {
    assertEquals(TrackingProvider.Capability.LOCAL_KINECT, controller.getTrackingCapability());
  }

  private static final class FakeTrackingProvider implements TrackingProvider {
    private FrameCallback callback;

    @Override
    public Capability getCapability() {
      return Capability.LOCAL_KINECT;
    }

    @Override
    public void setRgbPreviewListener(RgbPreviewBridge.Listener listener) {}

    @Override
    public void setDepthOverlayTransparent(boolean transparent) {}

    @Override
    public void start(
        android.content.Context context,
        FrameCallback callback,
        CameraController.StreamProfile cameraProfile) {
      this.callback = callback;
    }

    @Override
    public void stop() {
      callback = null;
    }

    void deliver(TrackingResult result, Bitmap silhouette) {
      if (callback != null) {
        callback.onTrackingFrame(result, silhouette);
      }
    }
  }

  /** Evita Sanbot SDK nos testes; só espelha os métodos usados pelo controller. */
  private abstract static class StubMirrorFeedback
      extends com.felipe.elftemplate.movement.RobotGameFeedback {
    StubMirrorFeedback() {
      super(null, null, null, null);
    }

    @Override
    public void stopAll() {}

    @Override
    public void setMirrorHead2D(int targetYawOffset, int targetPitchOffset) {}

    @Override
    public void performHeadNodYes() {}

    @Override
    public void performHeadShakeNo() {}
  }
}
