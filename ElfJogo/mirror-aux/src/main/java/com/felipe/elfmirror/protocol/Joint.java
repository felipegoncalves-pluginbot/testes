package com.felipe.elfmirror.protocol;

/** Articulação 3D (X/Y normalizados, Z em mm). */
public final class Joint {

  public float x = 0.5f;
  public float y = 0.5f;
  public int z = 0;

  public void set(float nx, float ny, int depthMm) {
    x = nx;
    y = ny;
    z = depthMm;
  }
}
