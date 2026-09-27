package com.felipe.elftemplate.tracking3d;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import com.felipe.elftemplate.tracking.Joint;
import com.felipe.elftemplate.tracking.TrackingResult;

/**
 * Overlay de depuração: máscara de segmentação ao fundo e esqueleto por cima.
 *
 * <p>Desenha em letterbox 4:3 para não esticar o quadro do Astra, seguindo o contrato de
 * `COORDINATE_FRAMES.md` (proibido esticar 4:3 em 16:9, porque desloca X e Y).
 */
public class Skeleton3dOverlayView extends View {

  private static final float ASPECT = 4f / 3f;

  private final Paint bonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint jointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint maskPaint = new Paint();
  private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Rect destRect = new Rect();
  private final TrackingResult snapshot = new TrackingResult();

  private Bitmap maskBitmap;
  private boolean hasPlayer;

  public Skeleton3dOverlayView(Context context) {
    super(context);
    init();
  }

  public Skeleton3dOverlayView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    bonePaint.setColor(Color.WHITE);
    bonePaint.setStrokeWidth(6f);
    bonePaint.setStyle(Paint.Style.STROKE);
    jointPaint.setColor(Color.rgb(0, 220, 255));
    jointPaint.setStyle(Paint.Style.FILL);
    maskPaint.setFilterBitmap(false);
    framePaint.setColor(Color.argb(120, 255, 255, 255));
    framePaint.setStyle(Paint.Style.STROKE);
    framePaint.setStrokeWidth(2f);
  }

  /** Recebe um novo frame; deve ser chamado na thread de UI. */
  public void update(TrackingResult result, Bitmap mask) {
    if (result != null) {
      snapshot.applyFrom(result);
      hasPlayer = result.isPlayerPresent;
    } else {
      hasPlayer = false;
    }
    maskBitmap = mask;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    computeLetterbox();
    if (maskBitmap != null && !maskBitmap.isRecycled()) {
      canvas.drawBitmap(maskBitmap, null, destRect, maskPaint);
    }
    canvas.drawRect(destRect, framePaint);
    if (!hasPlayer) {
      return;
    }
    drawBones();
    drawJoints(canvas);
    drawSkeletonLines(canvas);
  }

  /** Mantém a proporção 4:3 do Astra centralizada na view. */
  private void computeLetterbox() {
    int width = getWidth();
    int height = getHeight();
    int boxWidth = width;
    int boxHeight = (int) (width / ASPECT);
    if (boxHeight > height) {
      boxHeight = height;
      boxWidth = (int) (height * ASPECT);
    }
    int left = (width - boxWidth) / 2;
    int top = (height - boxHeight) / 2;
    destRect.set(left, top, left + boxWidth, top + boxHeight);
  }

  private void drawBones() {
    bonePaint.setColor(Color.WHITE);
  }

  private void drawSkeletonLines(Canvas canvas) {
    bone(canvas, snapshot.head, snapshot.neck);
    bone(canvas, snapshot.neck, snapshot.spine);
    bone(canvas, snapshot.neck, snapshot.leftShoulder);
    bone(canvas, snapshot.neck, snapshot.rightShoulder);
    bone(canvas, snapshot.leftShoulder, snapshot.leftElbow);
    bone(canvas, snapshot.leftElbow, snapshot.leftHand);
    bone(canvas, snapshot.rightShoulder, snapshot.rightElbow);
    bone(canvas, snapshot.rightElbow, snapshot.rightHand);
    bone(canvas, snapshot.spine, snapshot.leftHip);
    bone(canvas, snapshot.spine, snapshot.rightHip);
    bone(canvas, snapshot.leftHip, snapshot.leftKnee);
    bone(canvas, snapshot.leftKnee, snapshot.leftFoot);
    bone(canvas, snapshot.rightHip, snapshot.rightKnee);
    bone(canvas, snapshot.rightKnee, snapshot.rightFoot);
  }

  private void drawJoints(Canvas canvas) {
    dot(canvas, snapshot.head, 14f);
    dot(canvas, snapshot.leftHand, 12f);
    dot(canvas, snapshot.rightHand, 12f);
    dot(canvas, snapshot.leftShoulder, 8f);
    dot(canvas, snapshot.rightShoulder, 8f);
    dot(canvas, snapshot.leftElbow, 7f);
    dot(canvas, snapshot.rightElbow, 7f);
    dot(canvas, snapshot.leftHip, 7f);
    dot(canvas, snapshot.rightHip, 7f);
  }

  private void bone(Canvas canvas, Joint from, Joint to) {
    canvas.drawLine(mapX(from.x), mapY(from.y), mapX(to.x), mapY(to.y), bonePaint);
  }

  private void dot(Canvas canvas, Joint joint, float radius) {
    canvas.drawCircle(mapX(joint.x), mapY(joint.y), radius, jointPaint);
  }

  private float mapX(float normalized) {
    return destRect.left + (normalized * destRect.width());
  }

  private float mapY(float normalized) {
    return destRect.top + (normalized * destRect.height());
  }
}
