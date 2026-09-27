package com.felipe.elftemplate.tracking;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * Detector Cinemático de Gestos Faciais / Cabeça em Tempo Real (Head Gesture Detector). Analisa as
 * reversões de sinal na derivada temporal de posição da cabeça (Zero-Crossing Rate) para
 * identificar com precisão gestos de "NÃO" (Head Shake) e "SIM" (Head Nod).
 */
public class HeadGestureDetector {
  private static final String TAG = "HeadGestureDetector";

  private static class HeadSample {
    float x;
    float y;
    long time;

    HeadSample(float x, float y, long time) {
      this.x = x;
      this.y = y;
      this.time = time;
    }
  }

  private final List<HeadSample> history = new ArrayList<>();
  private static final int MAX_HISTORY = 25;
  private static final long GESTURE_WINDOW_MS = 1200;
  private static final long COOLDOWN_MS = 1500;
  private static final float MIN_AMPLITUDE =
      0.020f; // Mínimo de deslocamento para considerar movimento real

  private long lastGestureTriggerTime = 0;

  public synchronized KinectTrackingEngine.GestureType addSample(float x, float y, long timestamp) {
    history.add(new HeadSample(x, y, timestamp));
    if (history.size() > MAX_HISTORY) {
      history.remove(0);
    }

    if (timestamp - lastGestureTriggerTime < COOLDOWN_MS) {
      return KinectTrackingEngine.GestureType.IDLE;
    }

    if (history.size() < 6) {
      return KinectTrackingEngine.GestureType.IDLE;
    }

    // Filtra amostras dentro da janela temporal de análise
    long windowStart = timestamp - GESTURE_WINDOW_MS;
    List<HeadSample> validSamples = new ArrayList<>();
    for (HeadSample s : history) {
      if (s.time >= windowStart) {
        validSamples.add(s);
      }
    }

    if (validSamples.size() < 6) return KinectTrackingEngine.GestureType.IDLE;

    // 1. Contagem de Reversões de Direção no Eixo X (Horizontal - NÃO)
    int xReversals = 0;
    int lastXDirection = 0; // -1: esquerda, +1: direita
    float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;

    for (int i = 1; i < validSamples.size(); i++) {
      float dx = validSamples.get(i).x - validSamples.get(i - 1).x;
      float curX = validSamples.get(i).x;
      if (curX < minX) minX = curX;
      if (curX > maxX) maxX = curX;

      if (Math.abs(dx) >= 0.012f) {
        int dir = (dx > 0) ? 1 : -1;
        if (lastXDirection != 0 && dir != lastXDirection) {
          xReversals++;
        }
        lastXDirection = dir;
      }
    }

    // 2. Contagem de Reversões de Direção no Eixo Y (Vertical - SIM)
    int yReversals = 0;
    int lastYDirection = 0; // -1: cima, +1: baixo
    float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

    for (int i = 1; i < validSamples.size(); i++) {
      float dy = validSamples.get(i).y - validSamples.get(i - 1).y;
      float curY = validSamples.get(i).y;
      if (curY < minY) minY = curY;
      if (curY > maxY) maxY = curY;

      if (Math.abs(dy) >= 0.012f) {
        int dir = (dy > 0) ? 1 : -1;
        if (lastYDirection != 0 && dir != lastYDirection) {
          yReversals++;
        }
        lastYDirection = dir;
      }
    }

    float xSpan = maxX - minX;
    float ySpan = maxY - minY;

    // Regra de Decisão para "NÃO" (Head Shake):
    // Mínimo de 2 reversões no eixo X, amplitude X significativa e superior ao eixo Y
    if (xReversals >= 2 && xSpan >= MIN_AMPLITUDE && (xSpan > ySpan * 1.25f || yReversals < 2)) {
      lastGestureTriggerTime = timestamp;
      history.clear();
      Log.i(
          TAG,
          String.format(
              "[HEAD-GESTURE] Gesto detectado: CABEÇA NÃO (Head Shake) | Reversões X=%d | SpanX=%.3f | SpanY=%.3f",
              xReversals, xSpan, ySpan));
      return KinectTrackingEngine.GestureType.HEAD_SHAKE_NO;
    }

    // Regra de Decisão para "SIM" (Head Nod):
    // Mínimo de 2 reversões no eixo Y, amplitude Y significativa e superior ao eixo X
    if (yReversals >= 2 && ySpan >= MIN_AMPLITUDE && (ySpan > xSpan * 1.25f || xReversals < 2)) {
      lastGestureTriggerTime = timestamp;
      history.clear();
      Log.i(
          TAG,
          String.format(
              "[HEAD-GESTURE] Gesto detectado: CABEÇA SIM (Head Nod) | Reversões Y=%d | SpanY=%.3f | SpanX=%.3f",
              yReversals, ySpan, xSpan));
      return KinectTrackingEngine.GestureType.HEAD_NOD_YES;
    }

    return KinectTrackingEngine.GestureType.IDLE;
  }

  public synchronized void reset() {
    history.clear();
  }
}
