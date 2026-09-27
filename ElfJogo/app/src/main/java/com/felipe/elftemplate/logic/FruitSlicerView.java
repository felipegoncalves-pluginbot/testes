package com.felipe.elftemplate.logic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Visualizador Canvas 2D de alta performance para o jogo Fruit Slicer. */
public class FruitSlicerView extends View {

  public interface SliceListener {
    void onSlice(float x1, float y1, float x2, float y2);
  }

  private FruitSlicerGameEngine engine;
  private SliceListener sliceListener;

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint bladePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path bladePath = new Path();

  private static class TrailPoint {
    float x, y;
    long time;

    TrailPoint(float x, float y, long time) {
      this.x = x;
      this.y = y;
      this.time = time;
    }
  }

  private final List<TrailPoint> trailPoints = new ArrayList<>();
  private float lastTouchX = -1;
  private float lastTouchY = -1;

  public FruitSlicerView(Context context) {
    super(context);
    init();
  }

  public FruitSlicerView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    bladePaint.setStyle(Paint.Style.STROKE);
    bladePaint.setStrokeWidth(12f);
    bladePaint.setStrokeCap(Paint.Cap.ROUND);
    bladePaint.setStrokeJoin(Paint.Join.ROUND);

    textPaint.setTextSize(48f);
    textPaint.setColor(Color.WHITE);
    textPaint.setFakeBoldText(true);
    textPaint.setShadowLayer(8f, 2f, 2f, Color.BLACK);
  }

  public void setEngine(FruitSlicerGameEngine engine) {
    this.engine = engine;
  }

  public void setSliceListener(SliceListener listener) {
    this.sliceListener = listener;
  }

  public void addBladePoint(float normalizedX, float normalizedY) {
    float px = normalizedX * getWidth();
    float py = normalizedY * getHeight();
    long now = System.currentTimeMillis();
    trailPoints.add(new TrailPoint(px, py, now));

    if (lastTouchX >= 0 && lastTouchY >= 0 && sliceListener != null) {
      sliceListener.onSlice(
          lastTouchX / getWidth(), lastTouchY / getHeight(), normalizedX, normalizedY);
    }
    lastTouchX = px;
    lastTouchY = py;
    invalidate();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    float nx = event.getX() / getWidth();
    float ny = event.getY() / getHeight();
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        lastTouchX = event.getX();
        lastTouchY = event.getY();
        trailPoints.add(new TrailPoint(lastTouchX, lastTouchY, System.currentTimeMillis()));
        return true;
      case MotionEvent.ACTION_MOVE:
        addBladePoint(nx, ny);
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        lastTouchX = -1;
        lastTouchY = -1;
        return true;
      default:
        return super.onTouchEvent(event);
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    drawBladeTrail(canvas);
    if (engine == null) return;

    int w = getWidth();
    int h = getHeight();
    for (FruitSlicerGameEngine.GameItem item : engine.getActiveItems()) {
      paint.setColor(getItemColor(item));
      float cx = item.x * w;
      float cy = item.y * h;
      float r = item.radius * Math.min(w, h);
      canvas.drawCircle(cx, cy, r, paint);
    }

    canvas.drawText("Score: " + engine.getScore(), 30f, 60f, textPaint);
    canvas.drawText("Vidas: " + engine.getLives(), w - 200f, 60f, textPaint);
  }

  private int getItemColor(FruitSlicerGameEngine.GameItem item) {
    if (item.isBomb) return Color.DKGRAY;
    if (item.type == FruitSlicerGameEngine.ItemType.WATERMELON) return Color.parseColor("#22C55E");
    if (item.type == FruitSlicerGameEngine.ItemType.APPLE) return Color.parseColor("#EF4444");
    if (item.type == FruitSlicerGameEngine.ItemType.BANANA) return Color.parseColor("#EAB308");
    return Color.MAGENTA;
  }

  private void drawBladeTrail(Canvas canvas) {
    long now = System.currentTimeMillis();
    // Iterator em vez de removeIf: Collection.removeIf só existe a partir da API 24 e o Elf roda
    // Android 6 (API 23) sem core library desugaring — lá isso é NoSuchMethodError no onDraw.
    Iterator<TrailPoint> expired = trailPoints.iterator();
    while (expired.hasNext()) {
      if ((now - expired.next().time) > 200) {
        expired.remove();
      }
    }
    if (trailPoints.size() < 2) return;

    bladePath.reset();
    boolean first = true;
    for (TrailPoint tp : trailPoints) {
      if (first) {
        bladePath.moveTo(tp.x, tp.y);
        first = false;
      } else {
        bladePath.lineTo(tp.x, tp.y);
      }
    }
    bladePaint.setColor(Color.argb(220, 255, 255, 255));
    canvas.drawPath(bladePath, bladePaint);
  }
}
