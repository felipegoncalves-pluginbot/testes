package com.felipe.elftemplate.tracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/** View para desenhar uma marcação em volta do rosto detectado e a distância. */
public class FaceOverlayView extends View {

  private final Paint boxPaint = new Paint();
  private final Paint textPaint = new Paint();

  private Rect currentFaceBox;
  private float currentDistance;
  private float imageWidth = 640f;
  private float imageHeight = 480f;

  public FaceOverlayView(Context context, AttributeSet attrs) {
    super(context, attrs);

    boxPaint.setColor(Color.GREEN);
    boxPaint.setStrokeWidth(8.0f);
    boxPaint.setStyle(Paint.Style.STROKE);

    textPaint.setColor(Color.GREEN);
    textPaint.setTextSize(48f);
    textPaint.setStyle(Paint.Style.FILL);
    textPaint.setFakeBoldText(true);
    textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);
  }

  public void updateFace(Rect faceBoundingBox, float distanceMeters) {
    this.currentFaceBox = faceBoundingBox;
    this.currentDistance = distanceMeters;
    postInvalidate();
  }

  public void clearFace() {
    this.currentFaceBox = null;
    postInvalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (currentFaceBox == null) return;

    float scaleX = getWidth() / imageWidth;
    float scaleY = getHeight() / imageHeight;

    float left = currentFaceBox.left * scaleX;
    float top = currentFaceBox.top * scaleY;
    float right = currentFaceBox.right * scaleX;
    float bottom = currentFaceBox.bottom * scaleY;

    canvas.drawRect(left, top, right, bottom, boxPaint);

    String distText = String.format("%.2fm", currentDistance);
    canvas.drawText(distText, left, top - 20, textPaint);
  }
}
