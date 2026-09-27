package com.felipe.elftemplate;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import com.felipe.elftemplate.tracking.TrackingResult;
import com.felipe.elftemplate.tracking3d.Astra3dTelemetry;
import com.felipe.elftemplate.tracking3d.BodyTrackingSession;
import com.felipe.elftemplate.tracking3d.Skeleton3dOverlayView;
import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.SpeechManager;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tela de validação do motor métrico 3D no robô.
 *
 * <p>Mostra a máscara de segmentação, o esqueleto e os números físicos: altura e pitch estimados do
 * sensor, estatura e distância da pessoa, latência e FPS. É a tela para conferir no Sanbot se o
 * pipeline está correto, sem depender de logcat.
 */
public class Tracking3dActivity extends BindBaseActivity {

  private final BodyTrackingSession tracking = BodyTrackingSession.createLocal();
  private final TrackingResult uiResult = new TrackingResult();
  private final AtomicBoolean uiPosted = new AtomicBoolean(false);
  private final Object maskLock = new Object();

  private Skeleton3dOverlayView overlay;
  private TextView sensorLine;
  private TextView bodyLine;
  private TextView gestureLine;
  private SpeechManager speechManager;

  private volatile Bitmap maskBitmap;
  private volatile boolean stopped;
  private volatile boolean speechEnabled;
  private volatile String pendingSensorText = "";
  private volatile String pendingBodyText = "";
  private volatile String pendingGestureText = "";
  private String lastSpokenGesture = "";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    register(Tracking3dActivity.class);
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_tracking3d);
    overlay = findViewById(R.id.tracking3d_overlay);
    sensorLine = findViewById(R.id.tracking3d_sensor_line);
    bodyLine = findViewById(R.id.tracking3d_body_line);
    gestureLine = findViewById(R.id.tracking3d_gesture_line);
    findViewById(R.id.tracking3d_back).setOnClickListener(v -> finish());
    Button speechToggle = findViewById(R.id.tracking3d_toggle_speech);
    speechToggle.setOnClickListener(v -> toggleSpeech(speechToggle));
  }

  private void toggleSpeech(Button button) {
    speechEnabled = !speechEnabled;
    button.setText(
        speechEnabled
            ? getString(R.string.tracking3d_speech_off)
            : getString(R.string.tracking3d_speech_on));
  }

  @Override
  public void onMainServiceConnected() {
    // O SDK reconecta no onResume depois de um onStop; sem zerar a flag a tela ficava congelada.
    stopped = false;
    speechManager = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);
    tracking.start(this, this::onTrackingFrame);
  }

  /**
   * Chamado na thread de processamento do motor; nada de UI aqui.
   *
   * <p>A máscara de partes vem pronta do provider, junto com o resultado. Esta tela mantinha a sua
   * própria exportação de máscara, o que significava um segundo lugar para dar manutenção e um
   * segundo jeito de ficar dessincronizado do que o Modo Espelho mostra.
   */
  private void onTrackingFrame(TrackingResult result, Bitmap silhouette) {
    if (stopped) {
      return;
    }
    Astra3dTelemetry telemetry = tracking.getTelemetry();
    synchronized (maskLock) {
      maskBitmap = silhouette;
      uiResult.applyFrom(result);
    }
    pendingSensorText = telemetry.toHudLine();
    pendingBodyText = telemetry.toBodyLine() + "  |  " + telemetry.toLearnedLine();
    pendingGestureText = describeGesture(result);
    maybeSpeak(result);
    if (uiPosted.compareAndSet(false, true)) {
      runOnUiThread(this::renderFrame);
    }
  }

  private static String describeGesture(TrackingResult result) {
    if (result == null || !result.isPlayerPresent) {
      return "sem jogador";
    }
    return String.format(
        "gesto %s | maos %s %s | elev %.2f %.2f",
        result.activeGesture,
        result.isLeftHandRaised ? "E^" : "E_",
        result.isRightHandRaised ? "D^" : "D_",
        result.leftHandElevation,
        result.rightHandElevation);
  }

  private void maybeSpeak(TrackingResult result) {
    if (!speechEnabled || speechManager == null || result == null || !result.isPlayerPresent) {
      return;
    }
    String gesture = String.valueOf(result.activeGesture);
    if (gesture.equals(lastSpokenGesture) || "IDLE".equals(gesture)) {
      return;
    }
    lastSpokenGesture = gesture;
    speechManager.startSpeak(gesture);
  }

  private void renderFrame() {
    if (stopped || overlay == null) {
      uiPosted.set(false);
      return;
    }
    synchronized (maskLock) {
      overlay.update(uiResult, maskBitmap);
    }
    sensorLine.setText(pendingSensorText);
    bodyLine.setText(pendingBodyText);
    gestureLine.setText(pendingGestureText);
    uiPosted.set(false);
  }

  @Override
  protected void onStop() {
    stopped = true;
    tracking.stop();
    super.onStop();
  }
}
