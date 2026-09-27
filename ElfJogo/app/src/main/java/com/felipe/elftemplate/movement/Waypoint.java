package com.felipe.elftemplate.movement;

import androidx.annotation.NonNull;

/** Representa um ponto de destino nomeado no mapa. */
public class Waypoint {
  private final String name;
  private final double x;
  private final double y;

  /**
   * Cria um novo Waypoint.
   *
   * <p>Exemplo: new Waypoint("Cozinha", 1.5, -0.5);
   *
   * @param name Nome identificador do ponto.
   * @param x Coordenada X em metros.
   * @param y Coordenada Y em metros.
   */
  public Waypoint(String name, double x, double y) {
    this.name = name;
    this.x = x;
    this.y = y;
  }

  public String getName() {
    return name;
  }

  public double getX() {
    return x;
  }

  public double getY() {
    return y;
  }

  @NonNull
  @Override
  public String toString() {
    return String.format("%s (%.2f, %.2f)", name, x, y);
  }
}
