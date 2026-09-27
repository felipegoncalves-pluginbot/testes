package com.felipe.elftemplate.tracking;

import static org.junit.Assert.assertTrue;

import com.felipe.elftemplate.tracking.DepthBlobLimbScanner;
import org.junit.Test;

public class DepthBlobHandPickerTest {

  @Test
  public void armsDown_usesLateralExtremityNotLowestPixel() {
    DepthBlobLimbScanner.LimbScan scan = new DepthBlobLimbScanner.LimbScan();
    scan.leftMinY = 28;
    scan.leftMaxY = 40;
    scan.leftExtremityY = 26;
    scan.leftCount = 12;
    float y = 0f;
    for (int i = 0; i < 1; i++) {
      y = DepthBlobHandPicker.pickNormY(scan, 20f / 48f, 48, true);
    }
    assertTrue("não pode ser maxY (pé/quadril) y=" + y, y < 40f / 48f - 0.02f);
    assertTrue("mão abaixada fica ao lado do tronco y=" + y, y <= 20f / 48f + 0.23f);
  }

  @Test
  public void raisedArm_usesHighestLateralPixel() {
    DepthBlobLimbScanner.LimbScan scan = new DepthBlobLimbScanner.LimbScan();
    scan.leftMinY = 6;
    scan.leftMaxY = 36;
    scan.leftExtremityY = 8;
    scan.leftCount = 20;
    float shoulderY = 20f / 48f;
    float y = DepthBlobHandPicker.pickNormY(scan, shoulderY, 48, true);
    assertTrue("braço erguido y < ombro, y=" + y + " shoulder=" + shoulderY, y < shoulderY - 0.04f);
  }

  @Test
  public void nearFurnitureColumn_doesNotPickBlobCrownAsHand() {
    DepthBlobLimbScanner.LimbScan scan = new DepthBlobLimbScanner.LimbScan();
    scan.leftMinY = 8;
    scan.leftMaxY = 40;
    scan.leftExtremityY = 22;
    scan.leftCount = 40;
    float shoulderY = 22f / 48f;
    float y = DepthBlobHandPicker.pickNormY(scan, shoulderY, 48, true, true);
    assertTrue("coluna da cadeira não pode virar mão acima do ombro y=" + y, y >= shoulderY);
  }
}
