package com.felipe.elftemplate.movement;

public class RadarObject {
  public static final int TYPE_WALL = 0;
  public static final int TYPE_PERSON = 1;
  public static final int TYPE_DOG = 2;
  public static final int TYPE_CHAIR = 3;
  public static final int TYPE_UNKNOWN = 4;
  public static final int TYPE_CAR = 5;

  private int type;
  private double x;
  private double y;
  private String label;

  public RadarObject(int type, double x, double y, String label) {
    this.type = type;
    this.x = x;
    this.y = y;
    this.label = label;
  }

  public int getType() {
    return type;
  }

  public double getX() {
    return x;
  }

  public double getY() {
    return y;
  }

  public String getLabel() {
    return label;
  }
}
