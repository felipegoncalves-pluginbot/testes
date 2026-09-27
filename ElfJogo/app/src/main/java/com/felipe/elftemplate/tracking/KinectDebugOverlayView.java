package com.felipe.elftemplate.tracking;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * Visualizador de Diagnóstico Estilo Kinect (Botão Y do Xbox 360). Exibe a imagem de profundidade
 * 3D da Astra e o Esqueleto Articular Completo (Cabeça, Ombros, Cotovelos, Mãos, Tronco, Quadris,
 * Pés) com linhas neon conectadas.
 */
public class KinectDebugOverlayView extends View {

  private TrackingResult trackingResult;
  private Bitmap depthBitmap;

  private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint depthOverlayPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
  private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint bonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint jointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Rect srcRect = new Rect();
  private final Rect dstRect = new Rect();
  private final int[] destLtrb = new int[4];

  private boolean isOverlayVisible = true;
  private boolean minimalFullscreen = false;
  /** Pan RGB↔Astra (yaw da cabeça). Só no palco fullscreen; PiP fica em depth space. */
  private float rgbPanNorm;

  public KinectDebugOverlayView(Context context) {
    super(context);
    init();
  }

  public KinectDebugOverlayView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    textPaint.setTextSize(20f);
    textPaint.setColor(Color.GREEN);
    textPaint.setFakeBoldText(true);

    borderPaint.setColor(Color.parseColor("#00FF66"));
    borderPaint.setStyle(Paint.Style.STROKE);
    borderPaint.setStrokeWidth(3f);

    bonePaint.setColor(Color.parseColor("#00E5FF"));
    bonePaint.setStrokeWidth(5f);
    bonePaint.setStyle(Paint.Style.STROKE);
    bonePaint.setStrokeCap(Paint.Cap.ROUND);

    jointPaint.setStyle(Paint.Style.FILL);
    depthOverlayPaint.setFilterBitmap(true);
  }

  public void toggleVisibility() {
    isOverlayVisible = !isOverlayVisible;
    setVisibility(isOverlayVisible ? View.VISIBLE : View.GONE);
    invalidate();
  }

  /** Modo espelho: câmera RGB no fundo + silhueta Astra translúcida + esqueleto. */
  public void setMinimalFullscreen(boolean minimal) {
    minimalFullscreen = minimal;
    setBackgroundColor(Color.TRANSPARENT);
    invalidate();
  }

  /** Compensa o yaw da HD (peito≠cabeça). Kinect CoordinateMapper analog, só pan. */
  public void setRgbPanNorm(float panNorm) {
    rgbPanNorm = panNorm;
  }

  public void updateTracking(TrackingResult result, Bitmap silhouetteBitmap) {
    this.trackingResult = result;
    this.depthBitmap = silhouetteBitmap;
    if (getVisibility() == View.VISIBLE) {
      postInvalidate();
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int w = getWidth();
    int h = getHeight();
    if (w == 0 || h == 0) return;

    int ox;
    int oy;
    int viewW;
    int viewH;
    if (minimalFullscreen) {
      ox = 0;
      oy = 0;
      viewW = w;
      viewH = h;
    } else {
      bgPaint.setColor(Color.parseColor("#EE0A0F1D"));
      canvas.drawRoundRect(0, 0, w, h, 16, 16, bgPaint);
      canvas.drawRoundRect(0, 0, w, h, 16, 16, borderPaint);
      ox = 8;
      oy = 8;
      viewW = w - (ox * 2);
      viewH = h - 65;
    }

    // 2. Silhueta Astra no eixo do depth (OpenNI já espelha). RGB do espelho vira no canvas,
    // não aqui — scaleX no palco invertia os pontos em relação à câmera.
    int mapLeft = ox;
    int mapTop = oy;
    int mapRight = ox + viewW;
    int mapBottom = oy + viewH;
    if (depthBitmap != null && !depthBitmap.isRecycled()) {
      srcRect.set(0, 0, depthBitmap.getWidth(), depthBitmap.getHeight());
      if (minimalFullscreen) {
        // 4:3 Astra letterbox no 16:9 — não esticar (Kinect overlay-on-depth).
        PreviewViewport.letterboxDest(
            viewW, viewH, depthBitmap.getWidth(), depthBitmap.getHeight(), destLtrb);
        mapLeft = destLtrb[0];
        mapTop = destLtrb[1];
        mapRight = destLtrb[2];
        mapBottom = destLtrb[3];
        dstRect.set(mapLeft, mapTop, mapRight, mapBottom);
      } else {
        dstRect.set(ox, oy, ox + viewW, oy + viewH);
      }
      Paint depthPaint = minimalFullscreen ? depthOverlayPaint : bgPaint;
      canvas.drawBitmap(depthBitmap, srcRect, dstRect, depthPaint);
    }

    // 3. Renderização do Esqueleto Kinect
    if (trackingResult != null && trackingResult.isPlayerPresent) {
      // Tronco e Coluna
      drawBone(canvas, trackingResult.head, trackingResult.neck, mapLeft, mapTop, mapRight, mapBottom);
      drawBone(canvas, trackingResult.neck, trackingResult.spine, mapLeft, mapTop, mapRight, mapBottom);

      // Braço Esquerdo
      drawBone(
          canvas, trackingResult.neck, trackingResult.leftShoulder, mapLeft, mapTop, mapRight, mapBottom);
      if (isArmReliable(trackingResult, true)) {
        drawBone(
            canvas,
            trackingResult.leftShoulder,
            trackingResult.leftElbow,
            mapLeft,
            mapTop,
            mapRight,
            mapBottom);
        drawBone(
            canvas,
            trackingResult.leftElbow,
            trackingResult.leftHand,
            mapLeft,
            mapTop,
            mapRight,
            mapBottom);
      }

      // Braço Direito
      drawBone(
          canvas, trackingResult.neck, trackingResult.rightShoulder, mapLeft, mapTop, mapRight, mapBottom);
      if (isArmReliable(trackingResult, false)) {
        drawBone(
            canvas,
            trackingResult.rightShoulder,
            trackingResult.rightElbow,
            mapLeft,
            mapTop,
            mapRight,
            mapBottom);
        drawBone(
            canvas,
            trackingResult.rightElbow,
            trackingResult.rightHand,
            mapLeft,
            mapTop,
            mapRight,
            mapBottom);
      }

      // Quadris
      drawBone(
          canvas, trackingResult.spine, trackingResult.leftHip, mapLeft, mapTop, mapRight, mapBottom);
      drawBone(
          canvas, trackingResult.spine, trackingResult.rightHip, mapLeft, mapTop, mapRight, mapBottom);

      // Pernas e Pés (Renderizados apenas se estiverem dentro do enquadramento da câmera)
      if (trackingResult.hasLegsInFrame) {
        drawBone(
            canvas, trackingResult.leftHip, trackingResult.leftKnee, mapLeft, mapTop, mapRight, mapBottom);
        drawBone(
            canvas,
            trackingResult.rightHip,
            trackingResult.rightKnee,
            mapLeft,
            mapTop,
            mapRight,
            mapBottom);
        if (trackingResult.hasFeetInFrame) {
          drawBone(
              canvas,
              trackingResult.leftKnee,
              trackingResult.leftFoot,
              mapLeft,
              mapTop,
              mapRight,
              mapBottom);
          drawBone(
              canvas,
              trackingResult.rightKnee,
              trackingResult.rightFoot,
              mapLeft,
              mapTop,
              mapRight,
              mapBottom);
        }
      } else if (trackingResult.hasFeetInFrame) {
        drawBone(
            canvas, trackingResult.leftHip, trackingResult.leftFoot, mapLeft, mapTop, mapRight, mapBottom);
        drawBone(
            canvas,
            trackingResult.rightHip,
            trackingResult.rightFoot,
            mapLeft,
            mapTop,
            mapRight,
            mapBottom);
      }

      // Articulações Principais (Joints)
      drawJoint(canvas, trackingResult.head, Color.YELLOW, 11f, mapLeft, mapTop, mapRight, mapBottom);
      drawJoint(canvas, trackingResult.neck, Color.CYAN, 7f, mapLeft, mapTop, mapRight, mapBottom);
      drawJoint(canvas, trackingResult.spine, Color.GREEN, 9f, mapLeft, mapTop, mapRight, mapBottom);
      drawJoint(
          canvas,
          trackingResult.leftHip,
          Color.parseColor("#00E5FF"),
          6f,
          mapLeft,
          mapTop,
          mapRight,
          mapBottom);
      drawJoint(
          canvas,
          trackingResult.rightHip,
          Color.parseColor("#00E5FF"),
          6f,
          mapLeft,
          mapTop,
          mapRight,
          mapBottom);

      // Mão Esquerda (Ponto Ciano Neon)
      if (isArmReliable(trackingResult, true)) {
        int leftColor =
            trackingResult.isLeftHandOpen ? Color.parseColor("#00E5FF") : Color.parseColor("#00FF88");
        drawJoint(canvas, trackingResult.leftHand, leftColor, 10f, mapLeft, mapTop, mapRight, mapBottom);
      }
      // Mão Direita (Ponto Magenta Neon)
      if (isArmReliable(trackingResult, false)) {
        int rightColor =
            trackingResult.isRightHandOpen
                ? Color.parseColor("#FF007F")
                : Color.parseColor("#FF8800");
        drawJoint(
            canvas, trackingResult.rightHand, rightColor, 10f, mapLeft, mapTop, mapRight, mapBottom);
      }

      // Joelhos e Pés (Exibidos apenas se visíveis no FOV)
      if (trackingResult.hasLegsInFrame) {
        drawJoint(
            canvas,
            trackingResult.leftKnee,
            Color.parseColor("#00E5FF"),
            6f,
            mapLeft,
            mapTop,
            mapRight,
            mapBottom);
        drawJoint(
            canvas,
            trackingResult.rightKnee,
            Color.parseColor("#00E5FF"),
            6f,
            mapLeft,
            mapTop,
            mapRight,
            mapBottom);
      }
      if (trackingResult.hasFeetInFrame) {
        drawJoint(
            canvas, trackingResult.leftFoot, Color.YELLOW, 7f, mapLeft, mapTop, mapRight, mapBottom);
        drawJoint(
            canvas, trackingResult.rightFoot, Color.YELLOW, 7f, mapLeft, mapTop, mapRight, mapBottom);
      }
    }

    if (minimalFullscreen) {
      return;
    }

    // 4. Telemetria e Diagnóstico no Rodapé (somente modo PiP)
    if (trackingResult != null && trackingResult.isPlayerPresent) {
      textPaint.setColor(Color.parseColor("#00FF66"));
      textPaint.setTextSize(18f);
      String viewModeStr =
          trackingResult.diagnostics.isPoseFusionActive
              ? (trackingResult.diagnostics.isSeatedPose
                  ? "KINECT HÍBRIDO (Sentado)"
                  : "KINECT HÍBRIDO")
              : (trackingResult.hasFeetInFrame ? "CORPO INTEIRO" : "MODO TRONCO");
      String distStr =
          String.format("Z: %.2fm | %s", (trackingResult.playerDistanceZ / 1000.0f), viewModeStr);
      canvas.drawText(distStr, 12, h - 36, textPaint);

      textPaint.setColor(Color.parseColor("#FFD700"));
      String gestureStr = "Gesto: " + trackingResult.activeGesture.name();
      canvas.drawText(gestureStr, 12, h - 14, textPaint);
    } else {
      textPaint.setColor(Color.RED);
      textPaint.setTextSize(18f);
      canvas.drawText("AGUARDANDO CORPO...", 12, h - 34, textPaint);
      textPaint.setColor(Color.LTGRAY);
      textPaint.setTextSize(14f);
      canvas.drawText("Fique a 1.5m da câmera Astra", 12, h - 14, textPaint);
    }
  }

  /**
   * Confiança mínima para desenhar um braço.
   *
   * <p>Fica entre a junta obtida pelo caminho geodésico (0,7) e a sintetizada por proporção (0,2), ou
   * seja: desenha o que o sensor viu, esconde o que o modelo antropométrico chutou.
   */
  private static final float ARM_CONFIDENCE_MIN = 0.5f;

  /**
   * Um braço só é desenhado quando existe evidência de que ele foi medido.
   *
   * <p>Duas fontes possíveis. O motor métrico informa confiança por junta, e é ela que manda. O
   * pipeline de blob não informa nada, e aí vale o critério antigo de pixels laterais.
   */
  private static boolean isArmReliable(TrackingResult result, boolean left) {
    float confidence =
        left ? result.diagnostics.leftArmConfidence : result.diagnostics.rightArmConfidence;
    if (confidence != TrackingDiagnostics.CONFIDENCE_NOT_REPORTED) {
      return confidence >= ARM_CONFIDENCE_MIN;
    }
    if (result.diagnostics.isPoseFusionActive) {
      return true;
    }
    int px = left ? result.diagnostics.leftHandPixelCount : result.diagnostics.rightHandPixelCount;
    return px >= 3;
  }

  private void drawBone(
      Canvas canvas, Joint j1, Joint j2, int left, int top, int right, int bottom) {
    if (j1 == null || j2 == null) return;
    float pan = minimalFullscreen ? rgbPanNorm : 0f;
    float x1 = PreviewViewport.mapX(j1.x, left, right, pan);
    float y1 = PreviewViewport.mapY(j1.y, top, bottom);
    float x2 = PreviewViewport.mapX(j2.x, left, right, pan);
    float y2 = PreviewViewport.mapY(j2.y, top, bottom);
    canvas.drawLine(x1, y1, x2, y2, bonePaint);
  }

  private void drawJoint(
      Canvas canvas, Joint j, int color, float radius, int left, int top, int right, int bottom) {
    if (j == null) return;
    float pan = minimalFullscreen ? rgbPanNorm : 0f;
    float cx = PreviewViewport.mapX(j.x, left, right, pan);
    float cy = PreviewViewport.mapY(j.y, top, bottom);
    jointPaint.setColor(color);
    canvas.drawCircle(cx, cy, radius, jointPaint);
  }
}
