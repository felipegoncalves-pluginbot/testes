package com.felipe.elftemplate;

import android.app.Application;
import com.sanbot.debug.SanbotDebugHub;

/** Sobe o hub de debug com o processo, não só no Modo Espelho. */
public class ElfApp extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    SanbotDebugHub.get().start(this);
  }
}
