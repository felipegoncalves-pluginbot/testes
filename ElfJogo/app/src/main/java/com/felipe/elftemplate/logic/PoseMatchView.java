package com.felipe.elftemplate.logic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.felipe.elftemplate.tracking.KinectTrackingEngine;
import com.felipe.elftemplate.tracking.TrackingResult;

/**
 * Visualizador do jogo Pose Match / Just Dance. Exibe a silhueta da pose solicitada, barra de tempo
 * e espelhamento em tempo real.
 */
public class PoseMatchView extends View {

  private PoseMatchGameEngine engine;
  private TrackingResult currentTrackingResult;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF timerRect = new RectF();

  public PoseMatchView(Context context) {
    super(context);
    init();
  }

  public PoseMatchView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    textPaint.setTextAlign(Paint.Align.CENTER);
    textPaint.setFakeBoldText(true);
  }

  public void setEngine(PoseMatchGameEngine engine) {
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

    // Fundo Gradiente Roxo/Azul estilo Dance Central / Kinect
    paint.setColor(Color.parseColor("#1b1035"));
    canvas.drawRect(0, 0, w, h, paint);

    // 1. Barra Superior: Placar, Streak e Round
    textPaint.setColor(Color.parseColor("#00E5FF"));
    textPaint.setTextSize(44f);
    canvas.drawText("ROUND: " + engine.getRound(), w * 0.2f, 60f, textPaint);

    textPaint.setColor(Color.parseColor("#FFD700"));
    canvas.drawText("PONTOS: " + engine.getScore(), w * 0.5f, 60f, textPaint);

    textPaint.setColor(Color.parseColor("#FF5722"));
    canvas.drawText("STREAK: " + engine.getStreak() + "🔥", w * 0.8f, 60f, textPaint);

    // 2. Barra de Tempo Regressiva
    float timeRatio = engine.getTimeRemaining() / Math.max(0.1f, engine.getTimeLimit());
    timerRect.set(40, 90, w - 40, 110);
    paint.setColor(Color.DKGRAY);
    canvas.drawRoundRect(timerRect, 10, 10, paint);

    timerRect.set(40, 90, 40 + (w - 80) * timeRatio, 110);
    paint.setColor(timeRatio > 0.4f ? Color.GREEN : (timeRatio > 0.2f ? Color.YELLOW : Color.RED));
    canvas.drawRoundRect(timerRect, 10, 10, paint);

    // 3. Cartão Central com a Pose Alvo
    float cardTop = 140f;
    float cardBottom = h - 180f;
    RectF cardRect = new RectF(w * 0.15f, cardTop, w * 0.85f, cardBottom);
    paint.setColor(Color.parseColor("#2a1b4e"));
    canvas.drawRoundRect(cardRect, 24, 24, paint);

    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(36f);
    canvas.drawText("REPRODUZA A POSE:", w / 2f, cardTop + 50f, textPaint);

    // Nome da Pose em Destaque
    textPaint.setColor(Color.parseColor("#00FF66"));
    textPaint.setTextSize(56f);
    String poseName = formatPoseName(engine.getCurrentTargetPose());
    canvas.drawText(poseName, w / 2f, cardTop + 130f, textPaint);

    // Desenha Boneco Palito da Pose Solicitada
    drawPoseStickman(
        canvas, w / 2f, (cardTop + cardBottom) / 2f + 40f, engine.getCurrentTargetPose());

    // 4. Rodapé: Pose Detectada do Jogador em Tempo Real
    paint.setColor(Color.parseColor("#120b24"));
    canvas.drawRect(0, h - 120, w, h, paint);

    textPaint.setTextSize(34f);
    if (currentTrackingResult != null && currentTrackingResult.isPlayerPresent) {
      textPaint.setColor(Color.CYAN);
      canvas.drawText(
          "SUA POSE: " + formatPoseName(currentTrackingResult.activeGesture),
          w / 2f,
          h - 60,
          textPaint);
    } else {
      textPaint.setColor(Color.GRAY);
      canvas.drawText("Posicione-se em frente à câmera Orbbec Astra...", w / 2f, h - 60, textPaint);
    }
  }

  private String formatPoseName(KinectTrackingEngine.GestureType type) {
    if (type == null) return "AGUARDANDO...";
    switch (type) {
      case T_POSE:
        return "BRAÇOS EM T";
      case HANDS_UP:
        return "MÃOS PARA O ALTO";
      case JUMP:
        return "PULE NO AR!";
      case DUCK:
        return "AGACHE-SE!";
      case SWIPE_LEFT:
        return "ACENE PARA ESQUERDA";
      case SWIPE_RIGHT:
        return "ACENE PARA DIREITA";
      case SWING_UP:
        return "RAQUETADA PARA CIMA";
      default:
        return "PREPARE-SE";
    }
  }

  private void drawPoseStickman(
      Canvas canvas, float cx, float cy, KinectTrackingEngine.GestureType type) {
    paint.setColor(Color.WHITE);
    paint.setStrokeWidth(10f);
    paint.setStyle(Paint.Style.STROKE);

    // Cabeça
    canvas.drawCircle(cx, cy - 80f, 30f, paint);
    // Tronco
    canvas.drawLine(cx, cy - 50f, cx, cy + 40f, paint);
    // Pernas
    if (type == KinectTrackingEngine.GestureType.DUCK) {
      canvas.drawLine(cx, cy + 40f, cx - 40f, cy + 50f, paint);
      canvas.drawLine(cx, cy + 40f, cx + 40f, cy + 50f, paint);
    } else if (type == KinectTrackingEngine.GestureType.JUMP) {
      canvas.drawLine(cx, cy + 40f, cx - 30f, cy + 20f, paint);
      canvas.drawLine(cx, cy + 40f, cx + 30f, cy + 20f, paint);
    } else {
      canvas.drawLine(cx, cy + 40f, cx - 30f, cy + 100f, paint);
      canvas.drawLine(cx, cy + 40f, cx + 30f, cy + 100f, paint);
    }

    // Braços
    if (type == KinectTrackingEngine.GestureType.T_POSE) {
      canvas.drawLine(cx - 90f, cy - 20f, cx + 90f, cy - 20f, paint);
    } else if (type == KinectTrackingEngine.GestureType.HANDS_UP) {
      canvas.drawLine(cx, cy - 20f, cx - 60f, cy - 90f, paint);
      canvas.drawLine(cx, cy - 20f, cx + 60f, cy - 90f, paint);
    } else {
      canvas.drawLine(cx, cy - 20f, cx - 50f, cy + 20f, paint);
      canvas.drawLine(cx, cy - 20f, cx + 50f, cy + 20f, paint);
    }

    paint.setStyle(Paint.Style.FILL);
  }
}
