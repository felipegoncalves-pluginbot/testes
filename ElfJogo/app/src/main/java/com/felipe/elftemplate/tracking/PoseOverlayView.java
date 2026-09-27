package com.felipe.elftemplate.tracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** View para desenhar o esqueleto (landmarks) por cima do preview da câmera. */
public class PoseOverlayView extends View {

  private final Paint paint = new Paint();
  private Pose currentPose;

  public PoseOverlayView(Context context, AttributeSet attrs) {
    super(context, attrs);
    paint.setColor(Color.CYAN);
    paint.setStrokeWidth(8.0f);
    paint.setStyle(Paint.Style.STROKE);
  }

  public void updatePose(Pose pose) {
    this.currentPose = pose;
    postInvalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (currentPose == null) return;

    // Desenha conexões (esqueleto)
    drawBone(canvas, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER);
    drawBone(canvas, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW);
    drawBone(canvas, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST);
    drawBone(canvas, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW);
    drawBone(canvas, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST);
    drawBone(canvas, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP);
    drawBone(canvas, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP);
    drawBone(canvas, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP);

    // Desenha pontos pequenos para articulações
    paint.setStyle(Paint.Style.FILL);
    for (PoseLandmark landmark : currentPose.getAllPoseLandmarks()) {
      float x = (landmark.getPosition().x / 640f) * getWidth();
      float y = (landmark.getPosition().y / 480f) * getHeight();
      canvas.drawCircle(x, y, 4, paint);
    }
  }

  private void drawBone(Canvas canvas, int startType, int endType) {
    PoseLandmark start = currentPose.getPoseLandmark(startType);
    PoseLandmark end = currentPose.getPoseLandmark(endType);
    if (start != null && end != null) {
      float startX = (start.getPosition().x / 640f) * getWidth();
      float startY = (start.getPosition().y / 480f) * getHeight();
      float endX = (end.getPosition().x / 640f) * getWidth();
      float endY = (end.getPosition().y / 480f) * getHeight();
      canvas.drawLine(startX, startY, endX, endY, paint);
    }
  }
}
