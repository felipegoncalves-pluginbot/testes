package com.felipe.elftemplate.tracking;

/**
 * Modos de rastreamento inspirados no Kinect SDK ({@code SkeletonChooserMode} / player index).
 *
 * <ul>
 *   <li>{@link #SINGLE_CLOSEST} — pessoa mais próxima da câmera (ideal para eventos com público ao fundo)
 *   <li>{@link #SINGLE_STICKY} — trava o primeiro jogador até perder o rastreamento
 *   <li>{@link #SINGLE_CENTER} — prioriza quem está no centro do enquadramento
 *   <li>{@link #MULTI_CLOSEST_TWO} — rastreia até 2 pessoas (mais próximas)
 *   <li>{@link #MULTI_ALL} — até {@link TrackingModeConfig#getMaxPersons()} blobs humanos válidos
 * </ul>
 */
public enum TrackingMode {
  SINGLE_CLOSEST,
  SINGLE_STICKY,
  SINGLE_CENTER,
  MULTI_CLOSEST_TWO,
  MULTI_ALL
}
