package com.felipe.elftemplate.tracking;

import java.util.Random;

/**
 * Utilitário de testes para síntese determinística de quadros de profundidade 16-bit.
 * Permite reproduzir pessoas virtuais em 3D, gestos e ruído de sensor Orbbec Astra sem hardware físico.
 */
public class SyntheticDepthFixtures {

  public static final int DEFAULT_WIDTH = 64;
  public static final int DEFAULT_HEIGHT = 48;
  public static final short BACKGROUND_DEPTH = 3500; // 3.5m (fundo fora do fatiamento útil)
  public static final short PLAYER_BASE_DEPTH = 1500; // 1.5m (distância ideal de jogo)

  /** Cria um buffer de fundo limpo */
  public static short[] createBackground(int width, int height) {
    short[] buffer = new short[width * height];
    for (int i = 0; i < buffer.length; i++) {
      buffer[i] = BACKGROUND_DEPTH;
    }
    return buffer;
  }

  /** Desenha uma pessoa em pé em repouso (braços abaixados) no centro */
  public static short[] createStandingPerson(int width, int height, short depth) {
    short[] buffer = createBackground(width, height);
    int cx = width / 2;
    int cy = height / 2;

    // Cabeça
    fillCircle(buffer, width, height, cx, cy - 14, 4, (short) (depth - 20));
    // Tronco
    fillRect(buffer, width, height, cx - 5, cy - 10, cx + 5, cy + 8, depth);
    // Braços abaixados junto ao tronco (começando abaixo do ombro)
    fillRect(buffer, width, height, cx - 8, cy - 4, cx - 6, cy + 8, (short) (depth + 10));
    fillRect(buffer, width, height, cx + 6, cy - 4, cx + 8, cy + 8, (short) (depth + 10));
    // Pernas
    fillRect(buffer, width, height, cx - 4, cy + 9, cx - 2, cy + 18, (short) (depth + 20));
    fillRect(buffer, width, height, cx + 2, cy + 9, cx + 4, cy + 18, (short) (depth + 20));

    return buffer;
  }

  /** Desenha uma pessoa em T-Pose (ambos os braços esticados na horizontal) */
  public static short[] createTPosePerson(int width, int height, short depth) {
    short[] buffer = createBackground(width, height);
    int cx = width / 2;
    int cy = height / 2;

    // Cabeça
    fillCircle(buffer, width, height, cx, cy - 14, 4, (short) (depth - 20));
    // Tronco
    fillRect(buffer, width, height, cx - 5, cy - 10, cx + 5, cy + 8, depth);
    // Braços abertos horizontalmente (T-Pose na altura do peito/ombro)
    fillRect(buffer, width, height, cx - 20, cy - 8, cx - 5, cy - 5, (short) (depth - 10));
    fillRect(buffer, width, height, cx + 5, cy - 8, cx + 20, cy - 5, (short) (depth - 10));
    // Pernas
    fillRect(buffer, width, height, cx - 4, cy + 9, cx - 2, cy + 18, (short) (depth + 20));
    fillRect(buffer, width, height, cx + 2, cy + 9, cx + 4, cy + 18, (short) (depth + 20));

    return buffer;
  }

  /** Desenha uma pessoa com as duas mãos para cima */
  public static short[] createHandsUpPerson(int width, int height, short depth) {
    short[] buffer = createBackground(width, height);
    int cx = width / 2;
    int cy = height / 2;

    // Cabeça
    fillCircle(buffer, width, height, cx, cy - 14, 4, (short) (depth - 20));
    // Tronco
    fillRect(buffer, width, height, cx - 5, cy - 10, cx + 5, cy + 8, depth);
    // Ombro e braço esquerdo erguido conectado ao tronco
    fillRect(buffer, width, height, cx - 8, cy - 8, cx - 5, cy - 4, depth);
    fillRect(buffer, width, height, cx - 11, cy - 22, cx - 7, cy - 6, (short) (depth - 15));
    // Ombro e braço direito erguido conectado ao tronco
    fillRect(buffer, width, height, cx + 5, cy - 8, cx + 8, cy - 4, depth);
    fillRect(buffer, width, height, cx + 7, cy - 22, cx + 11, cy - 6, (short) (depth - 15));
    // Pernas
    fillRect(buffer, width, height, cx - 4, cy + 9, cx - 2, cy + 18, (short) (depth + 20));
    fillRect(buffer, width, height, cx + 2, cy + 9, cx + 4, cy + 18, (short) (depth + 20));

    return buffer;
  }

  /** Desenha uma pessoa agachada */
  public static short[] createDuckingPerson(int width, int height, short depth) {
    short[] buffer = createBackground(width, height);
    int cx = width / 2;
    int cy = height / 2 + 8; // Deslocado para baixo

    // Cabeça mais baixa
    fillCircle(buffer, width, height, cx, cy - 8, 4, (short) (depth - 20));
    // Tronco comprimido
    fillRect(buffer, width, height, cx - 7, cy - 4, cx + 7, cy + 8, depth);
    // Pernas dobradas
    fillRect(buffer, width, height, cx - 6, cy + 9, cx + 6, cy + 14, (short) (depth + 20));

    return buffer;
  }

  /** Pessoa sentada (tronco+cabeça, sem pernas longas). */
  public static short[] createSeatedPerson(int width, int height, short depth) {
    short[] buffer = createBackground(width, height);
    int cx = width / 2;
    int cy = (int) (height * 0.58f);
    fillCircle(buffer, width, height, cx, cy - 10, 4, (short) (depth - 20));
    fillRect(buffer, width, height, cx - 10, cy - 6, cx + 10, cy + 8, depth);
    fillRect(buffer, width, height, cx - 14, cy - 2, cx - 11, cy + 6, (short) (depth + 10));
    fillRect(buffer, width, height, cx + 11, cy - 2, cx + 14, cy + 6, (short) (depth + 10));
    return buffer;
  }

  /**
   * Sentado perto com encosto de cadeira (coluna vertical no mesmo Z). Reproduz o falso HANDS_UP
   * do viewer: topo do encosto virava punho acima da cabeça.
   */
  public static short[] createCloseSeatedPersonWithChair(int width, int height, short depth) {
    short[] buffer = createSeatedPerson(width, height, depth);
    int cy = (int) (height * 0.58f);
    fillRect(buffer, width, height, 2, cy - 16, 6, cy + 8, depth);
    return buffer;
  }

  /**
   * Sentado perto com lâmpada no teto (mesmo Z, blob separado). Viewer: punho em y≈0.09.
   */
  public static short[] createClosePersonWithOverheadLamp(int width, int height, short depth) {
    short[] buffer = createSeatedPerson(width, height, depth);
    fillCircle(buffer, width, height, (width * 3) / 4, 3, 4, depth);
    return buffer;
  }

  /** Adiciona ruído de sensor realista (dropout / speckle / buracos de profundidade) */
  public static void injectSensorNoise(short[] buffer, float noiseRatio, long seed) {
    Random random = new Random(seed);
    int totalNoisePixels = (int) (buffer.length * noiseRatio);
    for (int i = 0; i < totalNoisePixels; i++) {
      int idx = random.nextInt(buffer.length);
      // Ruído comum no Orbbec Astra: pixel preto (0mm = out of range) ou reflexo distante (4000mm)
      buffer[idx] = random.nextBoolean() ? (short) 0 : (short) 4000;
    }
  }

  /** Escala um fixture pequeno para resolução Astra (640x480) via amostragem nearest-neighbor. */
  public static short[] upscaleNearest(
      short[] src, int srcW, int srcH, int dstW, int dstH) {
    short[] dst = createBackground(dstW, dstH);
    for (int y = 0; y < dstH; y++) {
      int sy = y * srcH / dstH;
      int rowOff = y * dstW;
      int srcRow = sy * srcW;
      for (int x = 0; x < dstW; x++) {
        int sx = x * srcW / dstW;
        dst[rowOff + x] = src[srcRow + sx];
      }
    }
    return dst;
  }

  /** Blob lateral de ruído (ex.: cadeira/objeto ao lado do jogador). */
  public static void addSideBlob(
      short[] buffer, int width, int height, int cx, int cy, int radius, short depth) {
    fillCircle(buffer, width, height, cx, cy, radius, depth);
  }

  private static void fillRect(short[] buffer, int w, int h, int x1, int y1, int x2, int y2, short val) {
    int minX = Math.max(0, Math.min(x1, x2));
    int maxX = Math.min(w - 1, Math.max(x1, x2));
    int minY = Math.max(0, Math.min(y1, y2));
    int maxY = Math.min(h - 1, Math.max(y1, y2));

    for (int y = minY; y <= maxY; y++) {
      for (int x = minX; x <= maxX; x++) {
        buffer[y * w + x] = val;
      }
    }
  }

  private static void fillCircle(short[] buffer, int w, int h, int cx, int cy, int radius, short val) {
    int r2 = radius * radius;
    for (int y = Math.max(0, cy - radius); y <= Math.min(h - 1, cy + radius); y++) {
      for (int x = Math.max(0, cx - radius); x <= Math.min(w - 1, cx + radius); x++) {
        int dx = x - cx;
        int dy = y - cy;
        if (dx * dx + dy * dy <= r2) {
          buffer[y * w + x] = val;
        }
      }
    }
  }
}
