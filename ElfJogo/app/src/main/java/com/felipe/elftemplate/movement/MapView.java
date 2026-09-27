package com.felipe.elftemplate.movement;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Componente Visual para renderizar o Mapa Local e a trajetória de odometria do robô. */
public class MapView extends View {

  private final Paint pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint robotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint waypointTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private final Path reusablePath = new Path();
  private final Path robotShape = new Path();
  private static final float PIXELS_PER_METER = 100f;

  private List<float[]> pathHistory = new ArrayList<>();
  private float currentX = 0;
  private float currentY = 0;
  private float currentYaw = 0;
  private List<Waypoint> waypoints = new ArrayList<>();
  private List<float[]> wallPoints = new ArrayList<>();
  private List<RadarObject> radarObjects = new ArrayList<>();

  public MapView(Context context) {
    super(context);
    init();
  }

  public MapView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    pathPaint.setColor(Color.parseColor("#3b82f6"));
    pathPaint.setStyle(Paint.Style.STROKE);
    pathPaint.setStrokeWidth(6f);
    pathPaint.setStrokeCap(Paint.Cap.ROUND);

    robotPaint.setColor(Color.parseColor("#10b981"));
    robotPaint.setStyle(Paint.Style.FILL);

    targetPaint.setColor(Color.parseColor("#ef4444"));
    targetPaint.setStyle(Paint.Style.FILL);

    waypointTextPaint.setColor(Color.WHITE);
    waypointTextPaint.setTextSize(24f);
    waypointTextPaint.setFakeBoldText(true);

    gridPaint.setColor(Color.parseColor("#334155"));
    gridPaint.setStyle(Paint.Style.STROKE);
    gridPaint.setStrokeWidth(2f);

    wallPaint.setColor(Color.parseColor("#475569"));
    wallPaint.setStyle(Paint.Style.FILL);
  }

  public void updateRobotPose(float x, float y, float yawRad) {
    this.currentX = x;
    this.currentY = y;
    this.currentYaw = yawRad;
    invalidate();
  }

  public void setPathHistory(List<float[]> path) {
    this.pathHistory = path != null ? path : new ArrayList<>();
    invalidate();
  }

  public void setWaypoints(List<Waypoint> waypoints) {
    this.waypoints = waypoints != null ? waypoints : new ArrayList<>();
    invalidate();
  }

  public void setWallPoints(List<float[]> walls) {
    this.wallPoints = walls != null ? walls : new ArrayList<>();
    invalidate();
  }

  public void setRadarObjects(List<RadarObject> objects) {
    this.radarObjects = objects != null ? objects : new ArrayList<>();
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float cx = getWidth() / 2f;
    float cy = getHeight() / 2f;

    drawGrid(canvas, cx, cy);
    drawWalls(canvas, cx, cy);
    drawPath(canvas, cx, cy);
    drawWaypoints(canvas, cx, cy);
    drawRobot(canvas, cx, cy);
  }

  private void drawGrid(Canvas canvas, float cx, float cy) {
    float step = PIXELS_PER_METER;
    for (float x = cx % step; x < getWidth(); x += step) {
      canvas.drawLine(x, 0, x, getHeight(), gridPaint);
    }
    for (float y = cy % step; y < getHeight(); y += step) {
      canvas.drawLine(0, y, getWidth(), y, gridPaint);
    }
  }

  private void drawWalls(Canvas canvas, float cx, float cy) {
    for (float[] pt : wallPoints) {
      float sx = cx + (pt[0] * PIXELS_PER_METER);
      float sy = cy - (pt[1] * PIXELS_PER_METER);
      canvas.drawCircle(sx, sy, 4f, wallPaint);
    }
  }

  private void drawPath(Canvas canvas, float cx, float cy) {
    if (pathHistory.size() < 2) return;
    reusablePath.reset();
    boolean first = true;
    for (float[] pt : pathHistory) {
      float sx = cx + (pt[0] * PIXELS_PER_METER);
      float sy = cy - (pt[1] * PIXELS_PER_METER);
      if (first) {
        reusablePath.moveTo(sx, sy);
        first = false;
      } else {
        reusablePath.lineTo(sx, sy);
      }
    }
    canvas.drawPath(reusablePath, pathPaint);
  }

  private void drawWaypoints(Canvas canvas, float cx, float cy) {
    for (Waypoint wp : waypoints) {
      float sx = cx + ((float) wp.getX() * PIXELS_PER_METER);
      float sy = cy - ((float) wp.getY() * PIXELS_PER_METER);
      canvas.drawCircle(sx, sy, 14f, targetPaint);
      if (wp.getName() != null) {
        canvas.drawText(wp.getName(), sx + 18f, sy + 8f, waypointTextPaint);
      }
    }
  }

  private void drawRobot(Canvas canvas, float cx, float cy) {
    float rx = cx + (currentX * PIXELS_PER_METER);
    float ry = cy - (currentY * PIXELS_PER_METER);
    canvas.save();
    canvas.translate(rx, ry);
    canvas.rotate((float) Math.toDegrees(currentYaw));

    robotShape.reset();
    robotShape.moveTo(0, -20);
    robotShape.lineTo(-14, 16);
    robotShape.lineTo(14, 16);
    robotShape.close();
    canvas.drawPath(robotShape, robotPaint);
    canvas.restore();
  }
}
