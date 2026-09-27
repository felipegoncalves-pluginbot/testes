package com.felipe.elftemplate.movement;

/** Implementação do Logger para testes no console. */
public class ConsoleLogger implements Logger {
  @Override
  public void d(String tag, String message) {
    System.out.println("D/" + tag + ": " + message);
  }

  @Override
  public void i(String tag, String message) {
    System.out.println("I/" + tag + ": " + message);
  }

  @Override
  public void w(String tag, String message) {
    System.out.println("W/" + tag + ": " + message);
  }

  @Override
  public void e(String tag, String message) {
    System.err.println("E/" + tag + ": " + message);
  }
}
