package com.felipe.elftemplate;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import com.felipe.elftemplate.logic.HandCursorEngine;
import com.felipe.elftemplate.logic.HandCursorOverlayView;
import com.felipe.elftemplate.tracking.TrackingResult;
import com.felipe.elftemplate.tracking3d.BodyTrackingSession;
import com.sanbot.opensdk.base.BindBaseActivity;
import com.sanbot.opensdk.beans.FuncConstant;
import com.sanbot.opensdk.function.unit.HDCameraManager;
import com.sanbot.opensdk.function.unit.SpeechManager;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mouse virtual Kinect nativo sobre o Astra. Clique: empurrar para a câmera ou segurar ~0,5 s no alvo.
 *
 * <p>Usa a mesma sessão de rastreamento das outras telas ({@link BodyTrackingSession}). Antes tinha o
 * seu próprio caminho, com o motor de blob antigo e MoveNet, o que significava abrir a câmera HD, o
 * TensorFlow Lite e o Astra ao mesmo tempo para obter uma única coordenada de mão — e a mão que ele
 * obtinha vinha do detector que erra braço. Aqui o punho já chega medido em metros.
 */
public class HandCursorActivity extends BindBaseActivity {

  private final HandCursorEngine cursorEngine = new HandCursorEngine();
  private final BodyTrackingSession tracking = BodyTrackingSession.createLocal();
  private final TrackingResult pendingFrame = new TrackingResult();
  private final AtomicBoolean uiPosted = new AtomicBoolean(false);

  private HandCursorOverlayView overlay;
  private TextView statusView;
  private View contentRoot;
  private volatile boolean stopped;
  private volatile long frameSeq;
  private long drainedSeq;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    register(HandCursorActivity.class);
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_hand_cursor);
    overlay = findViewById(R.id.hand_cursor_overlay);
    statusView = findViewById(R.id.hand_cursor_status);
    contentRoot = findViewById(R.id.hand_cursor_panel);
    wireDemoButtons();
    findViewById(R.id.btn_cursor_back).setOnClickListener(v -> finish());
  }

  @Override
  public void onMainServiceConnected() {
    // O SDK reconecta no onResume depois de um onStop; sem zerar a flag o cursor ficava morto.
    stopped = false;
    SpeechManager speech = (SpeechManager) getUnitManager(FuncConstant.SPEECH_MANAGER);
    if (speech != null) {
      speech.startSpeak(
          "Modo mouse Kinect. Mova a mão erguida. Empurre para frente ou segure para clicar.");
    }
    HDCameraManager hdCamera = (HDCameraManager) getUnitManager(FuncConstant.HDCAMERA_MANAGER);
    tracking.attachHdCamera(hdCamera);
    tracking.start(this, (result, silhouette) -> onHandFrame(result));
  }

  private void onHandFrame(TrackingResult result) {
    if (stopped || result == null) {
      return;
    }
    pendingFrame.applyFrom(result);
    frameSeq++;
    if (uiPosted.compareAndSet(false, true)) {
      runOnUiThread(this::drainFrame);
    }
  }

  private void drainFrame() {
    long seq = frameSeq;
    if (stopped || overlay == null) {
      uiPosted.set(false);
      return;
    }
    int w = overlay.getWidth();
    int h = overlay.getHeight();
    if (w <= 0 || h <= 0) {
      uiPosted.set(false);
      overlay.post(this::drainFrame);
      return;
    }
    cursorEngine.update(pendingFrame, w, h);
    overlay.updateCursor(
        cursorEngine.getScreenX(),
        cursorEngine.getScreenY(),
        cursorEngine.isVisible(),
        cursorEngine.isPushing());
    if (cursorEngine.consumeClickPulse()) {
      int[] screenLoc = new int[2];
      overlay.getLocationOnScreen(screenLoc);
      boolean clicked =
          clickAt(
              contentRoot,
              (int) cursorEngine.getScreenX() + screenLoc[0],
              (int) cursorEngine.getScreenY() + screenLoc[1]);
      if (statusView != null) {
        statusView.setText(
            clicked
                ? getString(R.string.hand_cursor_status_click)
                : getString(R.string.hand_cursor_status_miss));
      }
    } else if (statusView != null && cursorEngine.isVisible()) {
      statusView.setText(getString(R.string.hand_cursor_status_track));
    } else if (statusView != null) {
      statusView.setText(getString(R.string.hand_cursor_status_idle));
    }
    drainedSeq = seq;
    uiPosted.set(false);
    if (drainedSeq != frameSeq && uiPosted.compareAndSet(false, true)) {
      runOnUiThread(this::drainFrame);
    }
  }

  private static boolean clickAt(View root, float x, float y) {
    View target = findClickableAt(root, (int) x, (int) y);
    return target != null && target.isShown() && target.performClick();
  }

  private static View findClickableAt(View view, int x, int y) {
    if (view == null || view.getVisibility() != View.VISIBLE) {
      return null;
    }
    Rect hit = new Rect();
    if (!view.getGlobalVisibleRect(hit) || !hit.contains(x, y)) {
      return null;
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = group.getChildCount() - 1; i >= 0; i--) {
        View nested = findClickableAt(group.getChildAt(i), x, y);
        if (nested != null) {
          return nested;
        }
      }
    }
    if (view.isClickable() && view.isEnabled()) {
      return view;
    }
    return null;
  }

  private void wireDemoButtons() {
    TextView status = statusView;
    findViewById(R.id.btn_cursor_a)
        .setOnClickListener(
            v -> {
              if (status != null) {
                status.setText(getString(R.string.hand_cursor_status_a));
              }
            });
    findViewById(R.id.btn_cursor_b)
        .setOnClickListener(
            v -> {
              if (status != null) {
                status.setText(getString(R.string.hand_cursor_status_b));
              }
            });
  }

  @Override
  protected void onStop() {
    stopped = true;
    tracking.stop();
    super.onStop();
  }
}
