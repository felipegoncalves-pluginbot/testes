package com.felipe.elftemplate.architecture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.felipe.elftemplate.logic.MirrorGameEngine;
import com.felipe.elftemplate.movement.RobotHeadController;
import com.felipe.elftemplate.tracking.ArmElevationMapper;
import com.felipe.elftemplate.tracking.Joint;
import com.felipe.elftemplate.tracking.PoseDepthFusion;
import com.felipe.elftemplate.tracking.PoseFrame;
import com.felipe.elftemplate.tracking.PoseLandmarkData;
import com.felipe.elftemplate.tracking.SyntheticDepthFixtures;
import com.felipe.elftemplate.tracking.TrackingResult;
import org.junit.Test;

/**
 * Guarda Arquitetural contra Regressão de IA em Cinemática e Orientação de Hardware.
 *
 * <p>Garante que:
 * 1. O robô sempre vire a cabeça para a direita quando o usuário estiver à direita (X > 0.5).
 * 2. O robô sempre vire a cabeça para a esquerda quando o usuário estiver à esquerda (X < 0.5).
 * 3. Braços abaixados ou em repouso ao lado do corpo NUNCA ativem as asas do robô.
 * 4. As coordenadas MoveNet (anatômicas) sejam unificadas com as de tela (screen-perspective).
 */
public class KinematicsOrientationGuardTest {

  @Test
  public void testHeadTrackingDirectionConsistency() {
    // Cenário 1: Usuário se move para a esquerda (lado esquerdo do usuário -> X=0.80 na câmera).
    // No Sanbot Elf, virar em direção à esquerda do usuário exige ângulo de hardware < 90°.
    MirrorGameEngine engineUserLeft = new MirrorGameEngine();
    TrackingResult resultUserLeft = new TrackingResult();
    resultUserLeft.isPlayerPresent = true;
    resultUserLeft.playerCentroidX = 0.80f;
    resultUserLeft.head = new Joint(0.80f, 0.30f, 1200);

    for (int i = 0; i < 10; i++) {
      engineUserLeft.processTracking(resultUserLeft);
    }
    int yawOffsetUserLeft = engineUserLeft.getTargetHeadYaw();
    int hwYawUserLeft = RobotHeadController.calculateHardwareYaw(yawOffsetUserLeft);

    assertTrue("Usuário à esquerda (X=0.80) deve gerar yawOffset negativo", yawOffsetUserLeft < 0);
    assertTrue("Usuário à esquerda (X=0.80) deve gerar ângulo de hardware < 90° (Esquerda no Sanbot)", hwYawUserLeft < 90);

    // Cenário 2: Usuário se move para a direita (lado direito do usuário -> X=0.20 na câmera).
    // No Sanbot Elf, virar em direção à direita do usuário exige ângulo de hardware > 90°.
    MirrorGameEngine engineUserRight = new MirrorGameEngine();
    TrackingResult resultUserRight = new TrackingResult();
    resultUserRight.isPlayerPresent = true;
    resultUserRight.playerCentroidX = 0.20f;
    resultUserRight.head = new Joint(0.20f, 0.30f, 1200);

    for (int i = 0; i < 10; i++) {
      engineUserRight.processTracking(resultUserRight);
    }
    int yawOffsetUserRight = engineUserRight.getTargetHeadYaw();
    int hwYawUserRight = RobotHeadController.calculateHardwareYaw(yawOffsetUserRight);

    assertTrue("Usuário à direita (X=0.20) deve gerar yawOffset positivo", yawOffsetUserRight > 0);
    assertTrue("Usuário à direita (X=0.20) deve gerar ângulo de hardware > 90° (Direita no Sanbot)", hwYawUserRight > 90);
  }

  @Test
  public void testArmsLoweredNeverRaiseWings() {
    MirrorGameEngine engine = new MirrorGameEngine();
    TrackingResult result = new TrackingResult();
    result.isPlayerPresent = true;
    result.leftHandElevation = 0.0f;
    result.rightHandElevation = 0.0f;
    result.isLeftHandRaised = false;
    result.isRightHandRaised = false;

    for (int i = 0; i < 10; i++) {
      engine.processTracking(result);
    }

    assertFalse("Asa esquerda DEVE estar abaixada quando elevação for 0", engine.isLeftWingUp());
    assertFalse("Asa direita DEVE estar abaixada quando elevação for 0", engine.isRightWingUp());
    assertEquals("Ângulo da asa esquerda deve ser WING_ANGLE_MIN (0°)", ArmElevationMapper.WING_ANGLE_MIN, engine.getTargetLeftWingAngle());
    assertEquals("Ângulo da asa direita deve ser WING_ANGLE_MIN (0°)", ArmElevationMapper.WING_ANGLE_MIN, engine.getTargetRightWingAngle());
  }

  @Test
  public void testPoseDepthFusionAlignsWithScreenPerspective() {
    // Cria landmarks do MoveNet onde usuário está de frente para a câmera com braços ABAIXADOS
    // MoveNet: RIGHT_SHOULDER e RIGHT_WRIST estão no lado ESQUERDO da imagem (x ~ 0.35)
    // MoveNet: LEFT_SHOULDER e LEFT_WRIST estão no lado DIREITO da imagem (x ~ 0.65)
    PoseLandmarkData[] landmarks = new PoseLandmarkData[PoseFrame.KEYPOINT_COUNT];
    landmarks[PoseFrame.NOSE] = new PoseLandmarkData(0.50f, 0.20f, 0.95f);
    landmarks[PoseFrame.LEFT_SHOULDER] = new PoseLandmarkData(0.65f, 0.35f, 0.90f);
    landmarks[PoseFrame.RIGHT_SHOULDER] = new PoseLandmarkData(0.35f, 0.35f, 0.90f);
    landmarks[PoseFrame.LEFT_ELBOW] = new PoseLandmarkData(0.68f, 0.50f, 0.90f);
    landmarks[PoseFrame.RIGHT_ELBOW] = new PoseLandmarkData(0.32f, 0.50f, 0.90f);
    // Punhos abaixo da cintura (braços abaixados)
    landmarks[PoseFrame.LEFT_WRIST] = new PoseLandmarkData(0.68f, 0.65f, 0.90f);
    landmarks[PoseFrame.RIGHT_WRIST] = new PoseLandmarkData(0.32f, 0.65f, 0.90f);
    landmarks[PoseFrame.LEFT_HIP] = new PoseLandmarkData(0.60f, 0.60f, 0.85f);
    landmarks[PoseFrame.RIGHT_HIP] = new PoseLandmarkData(0.40f, 0.60f, 0.85f);
    landmarks[PoseFrame.LEFT_KNEE] = new PoseLandmarkData(0.60f, 0.75f, 0.85f);
    landmarks[PoseFrame.RIGHT_KNEE] = new PoseLandmarkData(0.40f, 0.75f, 0.85f);
    landmarks[PoseFrame.LEFT_ANKLE] = new PoseLandmarkData(0.60f, 0.90f, 0.85f);
    landmarks[PoseFrame.RIGHT_ANKLE] = new PoseLandmarkData(0.40f, 0.90f, 0.85f);

    short[] depthData =
        SyntheticDepthFixtures.createStandingPerson(
            640, 480, SyntheticDepthFixtures.PLAYER_BASE_DEPTH);
    PoseFrame frame = new PoseFrame(landmarks, System.currentTimeMillis(), 640, 480);
    TrackingResult result = new TrackingResult();

    boolean fused = PoseDepthFusion.tryFuse(result, frame, depthData, 640, 480, System.currentTimeMillis());
    assertTrue("Fusão MoveNet + Depth deve ser bem-sucedida", fused);

    // O ombro esquerdo na tela (leftShoulder) deve ter X < ombro direito na tela (rightShoulder)
    assertTrue("leftShoulder.x deve ser < rightShoulder.x (perspectiva de tela)", result.leftShoulder.x < result.rightShoulder.x);
    assertTrue("leftHand.x deve ser < rightHand.x (perspectiva de tela)", result.leftHand.x < result.rightHand.x);

    // Punhos estão em y=0.65, ombros em y=0.35 (punhos bem abaixo dos ombros)
    assertTrue("Mão esquerda na tela deve estar abaixo do ombro", result.leftHand.y > result.leftShoulder.y);
    assertTrue("Mão direita na tela deve estar abaixo do ombro", result.rightHand.y > result.rightShoulder.y);
  }
}
