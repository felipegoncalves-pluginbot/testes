package com.felipe.elftemplate;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import com.felipe.elftemplate.logic.CprMusicPlayer;
import com.felipe.elftemplate.logic.CprStep;
import com.felipe.elftemplate.logic.CprSystemAudioPicker;
import com.felipe.elftemplate.logic.CprTrainingEngine;
import com.felipe.elftemplate.logic.CprTrainingView;
import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.SpeechManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modo treino de RCP para apresentações de enfermagem/medicina. Passos guiados, pulso visual
 * 100–120 BPM e música via seletor Android.
 */
public class CprActivity extends BindBaseActivity {

  private CprTrainingView trainingView;
  private Button btnBack;
  private Button btnPrevStep;
  private Button btnNextStep;
  private Button btnMusicPick;
  private Button btnMusicToggle;
  private Button btnMusicRestart;

  private CprTrainingEngine engine;
  private CprMusicPlayer musicPlayer;
  private SpeechManager speechManager;

  private final Handler loopHandler = new Handler(Looper.getMainLooper());
  private ExecutorService musicLoadExecutor;
  private boolean running;
  private long lastFrameMs;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    register(CprActivity.class);
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_cpr);

    trainingView = findViewById(R.id.cpr_training_view);
    btnBack = findViewById(R.id.btn_back);
    btnPrevStep = findViewById(R.id.btn_prev_step);
    btnNextStep = findViewById(R.id.btn_next_step);
    btnMusicPick = findViewById(R.id.btn_music_pick);
    btnMusicToggle = findViewById(R.id.btn_music_toggle);
    btnMusicRestart = findViewById(R.id.btn_music_restart);

    engine = new CprTrainingEngine();
    musicPlayer = new CprMusicPlayer();
    trainingView.setEngine(engine);

    trainingView.setMusicStatus(getString(R.string.cpr_music_pick_prompt));
    setMusicControlsEnabled(false);
    bindControls();
  }

  private void bindControls() {
    btnBack.setOnClickListener(v -> finish());
    btnPrevStep.setOnClickListener(
        v -> {
          engine.previousStep();
          onStepChanged();
        });
    btnNextStep.setOnClickListener(
        v -> {
          if (!engine.advanceStep()) {
            speak("Treino concluído. Parabéns pela prática!");
          }
          onStepChanged();
        });
    btnMusicPick.setOnClickListener(v -> openSystemAudioPicker());
    btnMusicToggle.setOnClickListener(v -> toggleMusic());
    btnMusicRestart.setOnClickListener(v -> restartMusic());
  }

  private void openSystemAudioPicker() {
    CprSystemAudioPicker.launch(this, getString(R.string.cpr_picker_title));
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != CprSystemAudioPicker.REQUEST_PICK_AUDIO
        || resultCode != RESULT_OK
        || data == null) {
      return;
    }
    final Uri uri = data.getData();
    if (uri == null) {
      return;
    }
    CprSystemAudioPicker.persistReadPermission(this, uri, data.getFlags());
    loadSelectedUri(uri);
  }

  private void loadSelectedUri(final Uri uri) {
    trainingView.setMusicStatus(getString(R.string.cpr_music_loading));
    refreshUi();
    if (musicLoadExecutor == null) {
      musicLoadExecutor = Executors.newSingleThreadExecutor();
    }
    final String displayName = CprSystemAudioPicker.resolveDisplayName(this, uri);
    musicLoadExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            final boolean ok = musicPlayer.load(CprActivity.this, uri, displayName);
            runOnUiThread(
                new Runnable() {
                  @Override
                  public void run() {
                    onTrackLoaded(ok, displayName);
                  }
                });
          }
        });
  }

  private void onTrackLoaded(boolean ok, String displayName) {
    if (ok) {
      trainingView.setMusicStatus(displayName);
      setMusicControlsEnabled(true);
      speak("Música selecionada: " + displayName);
    } else {
      trainingView.setMusicStatus(getString(R.string.cpr_music_load_error));
      setMusicControlsEnabled(false);
      speak("Não consegui abrir esse arquivo. Tente outro formato de áudio.");
    }
    refreshUi();
  }

  private void onStepChanged() {
    announceCurrentStep();
    refreshUi();
  }

  private void setMusicControlsEnabled(boolean enabled) {
    btnMusicToggle.setEnabled(enabled);
    btnMusicRestart.setEnabled(enabled);
  }

  private void toggleMusic() {
    if (!musicPlayer.isLoaded()) {
      return;
    }
    musicPlayer.togglePlayPause();
    refreshUi();
    if (musicPlayer.isPlaying()) {
      speak("Música iniciada. Siga o ritmo das compressões.");
    }
  }

  private void restartMusic() {
    if (!musicPlayer.isLoaded()) {
      return;
    }
    musicPlayer.restart();
    refreshUi();
    speak("Música reiniciada do começo.");
  }

  private void announceCurrentStep() {
    CprStep step = engine.getCurrentStep();
    speak(step.getTitle() + ". " + step.getInstruction());
    if (step.isCompressionPhase() && musicPlayer.isLoaded() && !musicPlayer.isPlaying()) {
      musicPlayer.play();
      refreshUi();
    }
  }

  private void refreshUi() {
    if (musicPlayer.isPlaying()) {
      btnMusicToggle.setText(R.string.cpr_btn_music_pause);
    } else {
      btnMusicToggle.setText(R.string.cpr_btn_music_play);
    }
    trainingView.invalidate();
  }

  @Override
  public void onMainServiceConnected() {
    speechManager = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);
    speak(
        "Modo treino de ressuscitação cardiopulmonar. "
            + "Toque em escolher música para carregar o metrônomo. Em emergência real, ligue 192.");
    onStepChanged();
    startLoop();
  }

  private void startLoop() {
    running = true;
    lastFrameMs = System.currentTimeMillis();
    loopHandler.post(loopRunnable);
  }

  private void stopLoop() {
    running = false;
    loopHandler.removeCallbacks(loopRunnable);
  }

  private final Runnable loopRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (!running) {
            return;
          }
          long now = System.currentTimeMillis();
          float delta = (now - lastFrameMs) / 1000f;
          lastFrameMs = now;

          boolean compressionStep = engine.getCurrentStep().isCompressionPhase();
          engine.update(delta, compressionStep);
          trainingView.invalidate();

          loopHandler.postDelayed(this, 16);
        }
      };

  private void speak(String text) {
    if (speechManager != null && text != null && !text.isEmpty()) {
      speechManager.startSpeak(text);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (speechManager != null && !running) {
      startLoop();
    }
  }

  @Override
  protected void onPause() {
    stopLoop();
    super.onPause();
  }

  @Override
  protected void onStop() {
    stopLoop();
    if (musicLoadExecutor != null) {
      musicLoadExecutor.shutdownNow();
      musicLoadExecutor = null;
    }
    if (musicPlayer != null) {
      musicPlayer.release();
    }
    super.onStop();
  }
}
