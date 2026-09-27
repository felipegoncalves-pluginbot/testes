package com.felipe.elftemplate.movement;

/** Interface para abstração de logs, permitindo trocar entre Android Log e Console Log (testes). */
public interface Logger {
  void d(String tag, String message);

  void i(String tag, String message);

  void w(String tag, String message);

  void e(String tag, String message);
}
