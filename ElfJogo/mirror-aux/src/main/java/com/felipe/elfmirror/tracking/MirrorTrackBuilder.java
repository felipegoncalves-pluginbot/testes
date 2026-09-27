package com.felipe.elfmirror.tracking;

import android.util.Log;
import androidx.annotation.NonNull;
import com.felipe.elfmirror.protocol.GestureType;
import com.felipe.elfmirror.protocol.MirrorProtocolCodec;
import com.felipe.elfmirror.protocol.TrackingResult;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;
import com.google.mlkit.vision.pose.PoseLandmark;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONException;

/**
 * Converte frame RGB do host em braços/gestos via ML Kit Pose (aux Kinect-style; corpo fica no robô).
 */
public final class MirrorTrackBuilder {

  private static final String TAG = "MirrorTrackBuilder";
  private static final float MIN_LANDMARK_SCORE = 0.35f;

  private final PoseDetector detector;
  private final TrackingResult scratch = new TrackingResult();
  private final ArmElevationTracker leftArmTracker = new ArmElevationTracker();
  private final ArmElevationTracker rightArmTracker = new ArmElevationTracker();
  private final AtomicBoolean inferBusy = new AtomicBoolean(false);

  public interface TrackCallback {
    void onTrackReady(String trackJson);

    void onInferError(String message);
  }

  public MirrorTrackBuilder() {
    AccuratePoseDetectorOptions options =
        new AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build();
    detector = PoseDetection.getClient(options);
  }

  public void close() {
    detector.close();
  }

  public void processFrame(MirrorProtocolCodec.FramePayload frame, TrackCallback callback) {
    if (frame == null || callback == null) {
      return;
    }
    if (!inferBusy.compareAndSet(false, true)) {
      return;
    }
    InputImage image =
        InputImage.fromByteArray(
            frame.rgbNv21,
            frame.rgbWidth,
            frame.rgbHeight,
            0,
            InputImage.IMAGE_FORMAT_NV21);
    detector
        .process(image)
        .addOnSuccessListener(
            pose -> {
              try {
                String json = buildTrackJson(frame, pose);
                if (json != null) {
                  callback.onTrackReady(json);
                }
              } catch (JSONException e) {
                callback.onInferError(e.getMessage());
              } finally {
                inferBusy.set(false);
              }
            })
        .addOnFailureListener(
            e -> {
              Log.w(TAG, "pose: " + e.getMessage());
              callback.onInferError(e.getMessage());
              inferBusy.set(false);
            });
  }

  private String buildTrackJson(MirrorProtocolCodec.FramePayload frame, Pose pose)
      throws JSONException {
    fillResult(scratch, frame, pose);
    if (!scratch.isPlayerPresent) {
      return null;
    }
    return MirrorProtocolCodec.encodeTrack(frame.seq, System.currentTimeMillis(), scratch);
  }

  private void fillResult(TrackingResult out, MirrorProtocolCodec.FramePayload frame, Pose pose) {
    out.reset();
    if (pose == null || pose.getAllPoseLandmarks().isEmpty()) {
      return;
    }

    float invW = 1f / Math.max(1, frame.rgbWidth);
    float invH = 1f / Math.max(1, frame.rgbHeight);

    // Espelho frontal: lado esquerdo da tela = lado direito anatômico (como PoseDepthFusion).
    Landmark screenLeftShoulder = readLandmark(pose, PoseLandmark.RIGHT_SHOULDER, invW, invH);
    Landmark screenRightShoulder = readLandmark(pose, PoseLandmark.LEFT_SHOULDER, invW, invH);
    Landmark screenLeftElbow = readLandmark(pose, PoseLandmark.RIGHT_ELBOW, invW, invH);
    Landmark screenRightElbow = readLandmark(pose, PoseLandmark.LEFT_ELBOW, invW, invH);
    Landmark screenLeftWrist = readLandmark(pose, PoseLandmark.RIGHT_WRIST, invW, invH);
    Landmark screenRightWrist = readLandmark(pose, PoseLandmark.LEFT_WRIST, invW, invH);
    Landmark headLm = readLandmark(pose, PoseLandmark.NOSE, invW, invH);

    if (!screenLeftShoulder.visible && !screenRightShoulder.visible) {
      return;
    }

    out.isPlayerPresent = true;
    writeLandmark(out.head, headLm);
    writeLandmark(out.leftShoulder, screenLeftShoulder);
    writeLandmark(out.rightShoulder, screenRightShoulder);
    writeLandmark(out.leftElbow, screenLeftElbow);
    writeLandmark(out.rightElbow, screenRightElbow);
    writeLandmark(out.leftHand, screenLeftWrist);
    writeLandmark(out.rightHand, screenRightWrist);

    float neckX = (out.leftShoulder.x + out.rightShoulder.x) * 0.5f;
    float neckY = (out.leftShoulder.y + out.rightShoulder.y) * 0.5f;
    int neckZ = (out.leftShoulder.z + out.rightShoulder.z) / 2;
    out.neck.set(neckX, neckY, neckZ);
    out.spine.set(neckX, neckY + 0.15f, neckZ);
    out.playerCentroidX = out.spine.x;
    out.playerCentroidY = out.spine.y;
    out.playerDistanceZ = neckZ > 0 ? neckZ : out.playerDistanceZ;

    boolean lateralSep = Math.abs(out.leftHand.x - out.rightHand.x) > 0.16f;
    out.leftHandElevation =
        leftArmTracker.update(
            out.leftShoulder.y,
            out.leftHand.y,
            screenLeftWrist.visible,
            lateralSep);
    out.rightHandElevation =
        rightArmTracker.update(
            out.rightShoulder.y,
            out.rightHand.y,
            screenRightWrist.visible,
            lateralSep);
    out.isLeftHandRaised = leftArmTracker.isRaised();
    out.isRightHandRaised = rightArmTracker.isRaised();
    out.activeGesture = resolveGesture(out);
    fuseLowerBody(out, pose, frame, invW, invH);
  }

  private static void fuseLowerBody(
      TrackingResult out,
      Pose pose,
      MirrorProtocolCodec.FramePayload frame,
      float invW,
      float invH) {
    Landmark screenLeftHip = readLandmark(pose, PoseLandmark.RIGHT_HIP, invW, invH);
    Landmark screenRightHip = readLandmark(pose, PoseLandmark.LEFT_HIP, invW, invH);
    Landmark screenLeftKnee = readLandmark(pose, PoseLandmark.RIGHT_KNEE, invW, invH);
    Landmark screenRightKnee = readLandmark(pose, PoseLandmark.LEFT_KNEE, invW, invH);
    Landmark screenLeftAnkle = readLandmark(pose, PoseLandmark.RIGHT_ANKLE, invW, invH);
    Landmark screenRightAnkle = readLandmark(pose, PoseLandmark.LEFT_ANKLE, invW, invH);
    writeLandmark(out.leftHip, screenLeftHip);
    writeLandmark(out.rightHip, screenRightHip);
    writeLandmark(out.leftKnee, screenLeftKnee);
    writeLandmark(out.rightKnee, screenRightKnee);
    writeLandmark(out.leftFoot, screenLeftAnkle);
    writeLandmark(out.rightFoot, screenRightAnkle);
    out.hasLegsInFrame = screenLeftKnee.visible || screenRightKnee.visible;
    out.hasFeetInFrame = screenLeftAnkle.visible || screenRightAnkle.visible;
  }

  private static GestureType resolveGesture(TrackingResult out) {
    if (out.isLeftHandRaised && out.isRightHandRaised) {
      return GestureType.HANDS_UP;
    }
    if (out.isLeftHandRaised) {
      return GestureType.LEFT_HAND_UP;
    }
    if (out.isRightHandRaised) {
      return GestureType.RIGHT_HAND_UP;
    }
    return GestureType.IDLE;
  }

  private static void writeLandmark(com.felipe.elfmirror.protocol.Joint dest, Landmark lm) {
    if (!lm.visible) {
      return;
    }
    dest.set(lm.normX, lm.normY, 0);
  }

  private static Landmark readLandmark(@NonNull Pose pose, int type, float invW, float invH) {
    PoseLandmark lm = pose.getPoseLandmark(type);
    if (lm == null || lm.getInFrameLikelihood() < MIN_LANDMARK_SCORE) {
      return Landmark.hidden();
    }
    return new Landmark(
        lm.getPosition().x * invW, lm.getPosition().y * invH, true);
  }

  private static final class Landmark {
    final float normX;
    final float normY;
    final boolean visible;

    Landmark(float normX, float normY, boolean visible) {
      this.normX = normX;
      this.normY = normY;
      this.visible = visible;
    }

    static Landmark hidden() {
      return new Landmark(0.5f, 0.5f, false);
    }
  }
}
