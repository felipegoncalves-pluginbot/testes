package com.felipe.elftemplate.logic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

/**
 * UI de treino RCP: emojis compatíveis com API 23, pulso visual no metrônomo e textos guiados.
 */
public class CprTrainingView extends View {

  private static final int COLOR_BG = Color.parseColor("#140808");
  private static final int COLOR_RED = Color.parseColor("#E53935");
  private static final int COLOR_RED_BRIGHT = Color.parseColor("#FF5252");
  private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");
  private static final int COLOR_MUTED = Color.parseColor("#FFCDD2");
  private static final int COLOR_HINT = Color.parseColor("#FFE0E0");
  private static final int COLOR_DOT_OFF = Color.parseColor("#5D1F1F");

  private CprTrainingEngine engine;
  private String musicStatus = "";
  private String disclaimer =
      "Ferramenta de TREINO — não substitui curso certificado. Em emergência real, ligue 192.";

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF pulseRect = new RectF();

  public CprTrainingView(Context context) {
    super(context);
    init();
  }

  public CprTrainingView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    textPaint.setTextAlign(Paint.Align.LEFT);
    textPaint.setFakeBoldText(true);
  }

  public void setEngine(CprTrainingEngine engine) {
    this.engine = engine;
    invalidate();
  }

  public void setMusicStatus(String status) {
    this.musicStatus = status == null ? "" : status;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int w = getWidth();
    int h = getHeight();
    if (w == 0 || h == 0 || engine == null) {
      return;
    }

    CprStep step = engine.getCurrentStep();

    paint.setColor(COLOR_BG);
    canvas.drawRect(0, 0, w, h, paint);

    drawStepDots(canvas, w);
    drawStepHeader(canvas, w);
    drawMainContent(canvas, w, h, step);
    drawFooter(canvas, w, h);
  }

  private void drawStepDots(Canvas canvas, int w) {
    int count = engine.getStepCount();
    float dotRadius = 12f;
    float spacing = 40f;
    float totalWidth = (count - 1) * spacing;
    float startX = (w - totalWidth) / 2f;
    float y = 52f;

    for (int i = 0; i < count; i++) {
      if (i == engine.getStepIndex()) {
        paint.setColor(COLOR_RED_BRIGHT);
      } else if (i < engine.getStepIndex()) {
        paint.setColor(COLOR_RED);
      } else {
        paint.setColor(COLOR_DOT_OFF);
      }
      canvas.drawCircle(startX + i * spacing, y, dotRadius, paint);
    }
  }

  private void drawStepHeader(Canvas canvas, int w) {
    CprStep step = engine.getCurrentStep();
    float maxTextWidth = w * 0.88f;
    float centerX = w / 2f;

    textPaint.setColor(COLOR_RED_BRIGHT);
    drawCenteredText(canvas, step.getTitle(), centerX, 118f, maxTextWidth, 52f);

    textPaint.setColor(COLOR_WHITE);
    drawCenteredText(canvas, step.getInstruction(), centerX, 168f, maxTextWidth, 32f);
  }

  private void drawMainContent(Canvas canvas, int w, int h, CprStep step) {
    float centerY = h * 0.54f;
    float maxTextWidth = w * 0.82f;
    float centerX = w / 2f;
    float emojiSize = Math.min(w, h) * 0.22f;

    if (step.isCompressionPhase()) {
      drawCompressionPulse(canvas, w, centerY);
    }

    drawStepEmoji(canvas, step, centerX, centerY, emojiSize);

    if (step.isCompressionPhase()) {
      textPaint.setColor(COLOR_WHITE);
      drawCenteredText(
          canvas,
          engine.isCompressPhase() ? "COMPRESSÃO" : "LIBERE",
          centerX,
          centerY + emojiSize * 1.1f,
          maxTextWidth,
          64f);

      textPaint.setColor(COLOR_MUTED);
      drawCenteredText(
          canvas,
          "Compressões: " + engine.getCompressionCount(),
          centerX,
          centerY + emojiSize * 1.45f,
          maxTextWidth,
          34f);
      drawCenteredText(
          canvas,
          "Siga o ritmo da música e o pulso na tela",
          centerX,
          centerY + emojiSize * 1.75f,
          maxTextWidth,
          28f);
    } else {
      textPaint.setColor(COLOR_HINT);
      drawCenteredText(canvas, step.getHint(), centerX, centerY + emojiSize * 1.1f, maxTextWidth, 30f);
    }
  }

  private void drawStepEmoji(Canvas canvas, CprStep step, float cx, float cy, float textSize) {
    textPaint.setTextSize(textSize);
    textPaint.setColor(COLOR_WHITE);
    textPaint.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics fm = textPaint.getFontMetrics();
    float baseline = cy - (fm.ascent + fm.descent) / 2f;
    canvas.drawText(step.getEmoji(), cx, baseline, textPaint);
    textPaint.setTextAlign(Paint.Align.LEFT);
  }

  private void drawCompressionPulse(Canvas canvas, int w, float centerY) {
    float baseRadius = Math.min(w, getHeight()) * 0.19f;
    float radius = baseRadius * engine.getPulseScale();
    float cx = w / 2f;

    paint.setColor(Color.argb(45, 255, 82, 82));
    canvas.drawCircle(cx, centerY, radius * 1.2f, paint);

    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(6f);
    paint.setColor(COLOR_RED);
    pulseRect.set(cx - radius, centerY - radius, cx + radius, centerY + radius);
    canvas.drawOval(pulseRect, paint);
    paint.setStyle(Paint.Style.FILL);
  }

  private void drawFooter(Canvas canvas, int w, int h) {
    float maxTextWidth = w * 0.9f;
    float centerX = w / 2f;

    textPaint.setColor(COLOR_MUTED);
    drawCenteredText(canvas, disclaimer, centerX, h - 96f, maxTextWidth, 20f);

    if (!musicStatus.isEmpty()) {
      drawCenteredText(canvas, "Música: " + musicStatus, centerX, h - 58f, maxTextWidth, 24f);
    }

    textPaint.setColor(COLOR_RED_BRIGHT);
    drawCenteredText(
        canvas, "Ritmo alvo: 100–120 compressões/min", centerX, h - 22f, maxTextWidth, 24f);
  }

  private void drawCenteredText(
      Canvas canvas, String text, float centerX, float topY, float maxWidth, float textSize) {
    if (text == null || text.isEmpty()) {
      return;
    }
    textPaint.setTextSize(textSize);
    StaticLayout layout =
        new StaticLayout(
            text, textPaint, (int) maxWidth, Layout.Alignment.ALIGN_CENTER, 1f, 6f, false);
    canvas.save();
    canvas.translate(centerX - maxWidth / 2f, topY);
    layout.draw(canvas);
    canvas.restore();
  }
}
