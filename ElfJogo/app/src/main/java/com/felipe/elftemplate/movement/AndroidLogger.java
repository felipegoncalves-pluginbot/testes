package com.felipe.elftemplate.movement;

import android.util.Log;

/** Implementação do Logger usando o Android Log. */
public class AndroidLogger implements Logger {
  @Override
  public void d(String tag, String message) {
    Log.d(tag, message);
  }

  @Override
  public void i(String tag, String message) {
    Log.i(tag, message);
  }

  @Override
  public void w(String tag, String message) {
    Log.w(tag, message);
  }

  @Override
  public void e(String tag, String message) {
    Log.e(tag, message);
  }
}
