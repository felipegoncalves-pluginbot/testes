package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreviewViewportTest {

  @Test
  public void matchingAspectFillsTheView() {
    int[] box = new int[4];
    PreviewViewport.centerCropDest(16, 9, 1280, 720, box);
    assertEquals(0, box[0]);
    assertEquals(0, box[1]);
    assertEquals(16, box[2]);
    assertEquals(9, box[3]);
  }

  @Test
  public void tallerSourceCropsTopAndBottom() {
    int[] box = new int[4];
    PreviewViewport.centerCropDest(16, 9, 4, 3, box);
    assertEquals(0, box[0]);
    assertEquals(16, box[2]);
    assertTrue("top cropped or equal", box[1] <= 0);
    assertTrue("bottom past view or equal", box[3] >= 9);
  }

  @Test
  public void widerSourceCropsLeftAndRight() {
    int[] box = new int[4];
    PreviewViewport.centerCropDest(4, 3, 16, 9, box);
    assertEquals(0, box[1]);
    assertEquals(3, box[3]);
    assertTrue(box[0] <= 0);
    assertTrue(box[2] >= 4);
  }

  @Test
  public void mapXKeepsOpticalLeftOnTheLeft() {
    float[] samples = new float[] {0.0f, 0.2f, 0.8f, 1.0f};
    for (int i = 0; i < samples.length; i++) {
      float mapped = com.felipe.elftemplate.tracking.PreviewViewport.mapX(samples[i], 0, 100);
      assertEquals(samples[i] * 100f, mapped, 0.001f);
    }
  }

  @Test
  public void hdPreviewDownsamplesTo640x360() {
    assertEquals(640, Nv21PreviewView.choosePreviewWidth(1280, 720));
    assertEquals(360, Nv21PreviewView.choosePreviewHeight(1280, 720));
  }

  @Test
  public void posePreviewFitsUnderCap() {
    assertTrue(Nv21PreviewView.choosePreviewWidth(640, 480) <= Nv21PreviewView.MAX_PREVIEW_W);
    assertTrue(Nv21PreviewView.choosePreviewHeight(640, 480) <= Nv21PreviewView.MAX_PREVIEW_H);
  }

  @Test
  public void letterboxFourByThreeIntoSixteenByNineHasSideBars() {
    int[] box = new int[4];
    PreviewViewport.letterboxDest(16, 9, 4, 3, box);
    assertTrue("barra esquerda", box[0] > 0);
    assertEquals(0, box[1]);
    assertTrue("barra direita", box[2] < 16);
    assertEquals(9, box[3]);
    assertTrue("largura 4:3", box[2] - box[0] < 16);
  }

  @Test
  public void yawPanShiftsOverlayTowardRgbCenter() {
    float pan = PreviewViewport.panNormFromHeadYaw(12);
    assertTrue("yaw+ → pan+ (RGB recentra jogador à esquerda da Astra)", pan > 0f);
    float[] nx = new float[] {0.20f, 0.22f, 0.30f};
    for (int i = 0; i < nx.length; i++) {
      float raw = PreviewViewport.mapX(nx[i], 0, 100);
      float shifted = PreviewViewport.mapX(nx[i], 0, 100, pan);
      assertTrue("junta vai para a direita no overlay", shifted > raw);
    }
  }
}
