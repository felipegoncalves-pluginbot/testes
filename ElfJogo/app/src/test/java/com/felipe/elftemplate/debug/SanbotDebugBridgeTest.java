package com.felipe.elftemplate.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.felipe.elftemplate.tracking.TrackingResult;
import com.sanbot.debug.DebugPose;
import com.sanbot.debug.DebugSensors;
import com.sanbot.debug.SanbotDebugRgb;
import org.junit.Test;

public class SanbotDebugBridgeTest {

  @Test
  public void fillCopiesHandsAndMotorCommands() {
    TrackingResult r = new TrackingResult();
    r.isPlayerPresent = true;
    r.leftHand.set(0.22f, 0.41f, 1400);
    r.rightHand.set(0.71f, 0.39f, 1400);
    r.playerDistanceZ = 1400;
    r.diagnostics.isPoseFusionActive = true;
    r.diagnostics.trackedPersonCount = 2;
    r.diagnostics.validPixelCount = 1800;
    r.diagnostics.bodyAspectHW = 1.7f;
    r.diagnostics.peakDepthZ = 2100;
    DebugPose p = new DebugPose();
    SanbotDebugBridge.fill(p, r, 10, -2, 33, 8);
    assertTrue(p.playerPresent);
    assertTrue(p.fused);
    assertEquals(0.22f, p.lHandX, 0.001f);
    assertEquals(0.41f, p.lHandY, 0.001f);
    assertEquals(10, p.yawCmd);
    assertEquals(33, p.leftWingCmd);
    assertEquals(1400, p.distanceZ);
    assertEquals(2, p.people);
    assertEquals(1800, p.pix);
    assertEquals(2100, p.peakZ);
    StringBuilder sb = new StringBuilder();
    p.appendJson(sb);
    String json = sb.toString();
    assertTrue(json.contains("\"yawCmd\":10"));
    assertTrue(json.contains("\"people\":2"));
  }

  @Test
  public void rgbDownsampleKeepsLumaWhenUvNeutral() {
    int srcW = 4;
    int srcH = 4;
    byte[] nv21 = new byte[srcW * srcH * 3 / 2];
    for (int i = 0; i < srcW * srcH; i++) {
      nv21[i] = (byte) 200;
    }
    for (int i = srcW * srcH; i < nv21.length; i++) {
      nv21[i] = (byte) 128;
    }
    int[] argb = new int[4];
    SanbotDebugRgb.fillArgbDownsample(nv21, srcW, srcH, argb, 2, 2);
    assertEquals(0xFFC8C8C8, argb[0]);
    assertEquals(0xFFC8C8C8, argb[3]);
  }

  @Test
  public void rgbDownsampleHighVIsMoreRedThanBlue() {
    int srcW = 4;
    int srcH = 4;
    byte[] nv21 = new byte[srcW * srcH * 3 / 2];
    for (int i = 0; i < srcW * srcH; i++) {
      nv21[i] = (byte) 128;
    }
    for (int i = srcW * srcH; i < nv21.length; i += 2) {
      nv21[i] = (byte) 255;
      nv21[i + 1] = (byte) 128;
    }
    int[] argb = new int[1];
    SanbotDebugRgb.fillArgbDownsample(nv21, srcW, srcH, argb, 1, 1);
    int r = (argb[0] >> 16) & 0xFF;
    int b = argb[0] & 0xFF;
    assertTrue(r > b);
    assertTrue(r > 200);
  }

  @Test
  public void sensorsJsonExposesChestIrAndGyro() {
    DebugSensors s = new DebugSensors();
    s.setIr(13, 42);
    s.setIr(11, 70);
    s.gyroYaw = 12.5f;
    s.pirFront = true;
    StringBuilder sb = new StringBuilder();
    s.appendJson(sb);
    String json = sb.toString();
    assertTrue(json.contains("\"chest\":42"));
    assertTrue(json.contains("\"torsoL\":70"));
    assertTrue(json.contains("\"pirF\":true"));
    assertTrue(json.contains("12.5"));
  }
}
