package com.sanbot.debug;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

/** Desenha silhueta + esqueleto num bitmap pequeno (RAM baixa). */
final class SanbotDebugRenderer {

  static final int WIDTH = 160;
  static final int HEIGHT = 120;

  private final Paint bone = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint joint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Rect src = new Rect();
  private final Rect dst = new Rect();

  SanbotDebugRenderer() {
    bone.setColor(Color.parseColor("#00E5FF"));
    bone.setStrokeWidth(2.5f);
    bone.setStyle(Paint.Style.STROKE);
    joint.setStyle(Paint.Style.FILL);
    text.setColor(Color.GREEN);
    text.setTextSize(10f);
  }

  void draw(Bitmap out, DebugPose p, Bitmap silhouette) {
    if (out == null) {
      return;
    }
    Canvas c = new Canvas(out);
    c.drawColor(Color.parseColor("#0A0F1D"));
    int w = out.getWidth();
    int h = out.getHeight();
    if (silhouette != null && !silhouette.isRecycled()) {
      src.set(0, 0, silhouette.getWidth(), silhouette.getHeight());
      dst.set(0, 0, w, h - 14);
      c.drawBitmap(silhouette, src, dst, null);
    }
    int vw = w;
    int vh = h - 14;
    if (p != null && p.playerPresent) {
      line(c, p.headX, p.headY, p.neckX, p.neckY, vw, vh);
      line(c, p.neckX, p.neckY, p.spineX, p.spineY, vw, vh);
      line(c, p.neckX, p.neckY, p.lShoulderX, p.lShoulderY, vw, vh);
      line(c, p.lShoulderX, p.lShoulderY, p.lElbowX, p.lElbowY, vw, vh);
      line(c, p.lElbowX, p.lElbowY, p.lHandX, p.lHandY, vw, vh);
      line(c, p.neckX, p.neckY, p.rShoulderX, p.rShoulderY, vw, vh);
      line(c, p.rShoulderX, p.rShoulderY, p.rElbowX, p.rElbowY, vw, vh);
      line(c, p.rElbowX, p.rElbowY, p.rHandX, p.rHandY, vw, vh);
      line(c, p.spineX, p.spineY, p.lHipX, p.lHipY, vw, vh);
      line(c, p.spineX, p.spineY, p.rHipX, p.rHipY, vw, vh);
      dot(c, p.headX, p.headY, Color.YELLOW, 4f, vw, vh);
      dot(c, p.lHandX, p.lHandY, Color.parseColor("#00E5FF"), 4f, vw, vh);
      dot(c, p.rHandX, p.rHandY, Color.parseColor("#FF007F"), 4f, vw, vh);
    }
    String line =
        (p == null ? "no-pose" : (p.playerPresent ? "Z " + p.distanceZ + " " + p.mode : "WAIT"))
            + " y"
            + (p == null ? 0 : p.yawCmd)
            + " L"
            + (p == null ? 0 : p.leftWingCmd)
            + " R"
            + (p == null ? 0 : p.rightWingCmd);
    c.drawText(line, 4, h - 3, text);
  }

  private void line(Canvas c, float x1, float y1, float x2, float y2, int vw, int vh) {
    c.drawLine(x1 * vw, y1 * vh, x2 * vw, y2 * vh, bone);
  }

  private void dot(Canvas c, float x, float y, int color, float r, int vw, int vh) {
    joint.setColor(color);
    c.drawCircle(x * vw, y * vh, r, joint);
  }
}
