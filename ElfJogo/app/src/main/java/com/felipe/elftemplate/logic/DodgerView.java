package com.felipe.elftemplate.logic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.felipe.elftemplate.tracking.TrackingResult;

/**
 * Visualizador do jogo Dodger Runner estilo Kinect Adventures. Renderiza a pista de corrida, os
 * obstáculos dinâmicos e o avatar do jogador controlado por movimento corporal.
 */
public class DodgerView extends View {

  private DodgerGameEngine engine;
  private TrackingResult currentTrackingResult;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF obsRect = new RectF();

  public DodgerView(Context context) {
    super(context);
    init();
  }

  public DodgerView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    textPaint.setFakeBoldText(true);
  }

  public void setEngine(DodgerGameEngine engine) {
    this.engine = engine;
  }

  public void updateTracking(TrackingResult result) {
    this.currentTrackingResult = result;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int w = getWidth();
    int h = getHeight();
    if (w == 0 || h == 0 || engine == null) return;

    // Fundo Cyberpunk / Pista Noturna
    canvas.drawColor(Color.parseColor("#0d1117"));

    // 1. Pista de Corrida (Linhas de pista)
    paint.setColor(Color.parseColor("#30363d"));
    paint.setStrokeWidth(4f);
    canvas.drawLine(w * 0.33f, 0, w * 0.33f, h, paint);
    canvas.drawLine(w * 0.66f, 0, w * 0.66f, h, paint);

    // Linhas de horizonte dinâmicas
    paint.setColor(Color.parseColor("#1f6feb"));
    float offset = (engine.getDistance() * 20f) % 60f;
    for (float y = offset; y < h; y += 60f) {
      canvas.drawLine(0, y, w, y, paint);
    }

    // 2. Renderiza Obstáculos
    synchronized (engine.getActiveObstacles()) {
      for (DodgerGameEngine.Obstacle obs : engine.getActiveObstacles()) {
        float ox = obs.x * w;
        float oy = obs.y * h;
        float ow = obs.width * w;
        float oh = obs.height * h;

        obsRect.set(ox - ow / 2f, oy - oh / 2f, ox + ow / 2f, oy + oh / 2f);

        switch (obs.type) {
          case LOW_HURDLE:
            paint.setColor(Color.parseColor("#FF5722")); // Laranja Neon
            canvas.drawRoundRect(obsRect, 12, 12, paint);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(24f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("PULE!", ox, oy + 8f, textPaint);
            break;
          case HIGH_BARRIER:
            paint.setColor(Color.parseColor("#00E5FF")); // Laser Ciano
            canvas.drawRoundRect(obsRect, 8, 8, paint);
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(24f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("AGACHE!", ox, oy + 8f, textPaint);
            break;
          case LEFT_BLOCK:
          case RIGHT_BLOCK:
            paint.setColor(Color.parseColor("#E91E63")); // Rosa Choque
            canvas.drawRoundRect(obsRect, 16, 16, paint);
            break;
        }
      }
    }

    // 3. Renderiza Avatar do Jogador na base da tela
    float playerX = 0.5f;
    boolean isJumping = false;
    boolean isDucking = false;

    if (currentTrackingResult != null && currentTrackingResult.isPlayerPresent) {
      playerX = currentTrackingResult.playerCentroidX;
      isJumping = currentTrackingResult.isJumping;
      isDucking = currentTrackingResult.isDucking;
    }

    float px = playerX * w;
    float py = h * 0.82f;
    float pr = 36f;

    if (isJumping) {
      py -= 70f; // Salto vertical no ar
      paint.setColor(Color.parseColor("#00FF66")); // Verde salto
    } else if (isDucking) {
      pr = 22f; // Agacha (menor altura)
      paint.setColor(Color.parseColor("#FFD700")); // Amarelo agachado
    } else {
      paint.setColor(Color.parseColor("#58A6FF")); // Azul normal
    }

    canvas.drawCircle(px, py, pr, paint);
    paint.setColor(Color.WHITE);
    canvas.drawCircle(px, py, pr * 0.4f, paint);

    // 4. UI Superior: Distância, Pontos e Vidas
    textPaint.setTextAlign(Paint.Align.LEFT);
    textPaint.setTextSize(44f);
    textPaint.setColor(Color.parseColor("#00E5FF"));
    canvas.drawText(String.format("DISTÂNCIA: %.0fm", engine.getDistance()), 40f, 60f, textPaint);

    textPaint.setColor(Color.parseColor("#FFD700"));
    canvas.drawText("PONTOS: " + engine.getScore(), 40f, 120f, textPaint);

    // Vidas ❤️
    StringBuilder livesStr = new StringBuilder();
    for (int i = 0; i < engine.getLives(); i++) livesStr.append("❤️ ");
    textPaint.setColor(Color.RED);
    textPaint.setTextSize(40f);
    canvas.drawText(livesStr.toString(), w - 220f, 60f, textPaint);

    if (engine.isGameOver()) {
      paint.setColor(Color.parseColor("#CC000000"));
      canvas.drawRect(0, 0, w, h, paint);

      textPaint.setColor(Color.RED);
      textPaint.setTextSize(80f);
      textPaint.setTextAlign(Paint.Align.CENTER);
      canvas.drawText("FIM DE JOGO!", w / 2f, h / 2f - 30f, textPaint);

      textPaint.setColor(Color.WHITE);
      textPaint.setTextSize(44f);
      canvas.drawText(
          String.format("DISTÂNCIA FINAL: %.0f METROS", engine.getDistance()),
          w / 2f,
          h / 2f + 40f,
          textPaint);
    }
  }
}
