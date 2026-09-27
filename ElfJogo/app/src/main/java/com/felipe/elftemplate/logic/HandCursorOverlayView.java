package com.felipe.elftemplate.logic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Desenha o ponteiro da mão (estilo Kinect cursor). */
public class HandCursorOverlayView extends View {

  private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float cursorX;
  private float cursorY;
  private boolean visible;
  private boolean pushing;

  public HandCursorOverlayView(Context context) {
    super(context);
    init();
  }

  public HandCursorOverlayView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    setWillNotDraw(false);
    cursorPaint.setColor(0xFF00E5FF);
    cursorPaint.setStyle(Paint.Style.FILL);
    ringPaint.setColor(0xCCFFFFFF);
    ringPaint.setStyle(Paint.Style.STROKE);
    ringPaint.setStrokeWidth(4f);
  }

  public void updateCursor(float x, float y, boolean visible, boolean pushing) {
    cursorX = x;
    cursorY = y;
    this.visible = visible;
    this.pushing = pushing;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (!visible) {
      return;
    }
    float radius = pushing ? 28f : 22f;
    canvas.drawCircle(cursorX, cursorY, radius + 6f, ringPaint);
    canvas.drawCircle(cursorX, cursorY, radius, cursorPaint);
  }
}
