package com.felipe.elftemplate.tracking;

import java.util.ArrayList;
import java.util.List;

/** Mock para substituir o ML Kit Pose (agora proibido). */
public class Pose {
  public PoseLandmark getPoseLandmark(int type) {
    return null;
  }

  public List<PoseLandmark> getAllPoseLandmarks() {
    return new ArrayList<>();
  }
}
