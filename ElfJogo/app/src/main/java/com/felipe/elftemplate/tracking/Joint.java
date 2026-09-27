package com.felipe.elftemplate.tracking;

/** Articulação 3D de esqueleto corporal (X, Y normalizados [0..1] e Z em milímetros). */
public class Joint {
  public float x = 0.5f;
  public float y = 0.5f;
  public int z = 0;

  public Joint() {}

  public Joint(float x, float y, int z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public void set(float x, float y, int z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public void smoothWith(Joint target, float alpha) {
    this.x = (alpha * target.x) + ((1.0f - alpha) * this.x);
    this.y = (alpha * target.y) + ((1.0f - alpha) * this.y);
    this.z = (int) ((alpha * target.z) + ((1.0f - alpha) * this.z));
  }

  @Override
  public String toString() {
    return String.format("(%.2f, %.2f, %dmm)", x, y, z);
  }
}
