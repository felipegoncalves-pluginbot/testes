package com.felipe.elftemplate.logic;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import java.io.IOException;

/** Reproduz áudio do armazenamento (caminho ou URI do seletor Android) para metrônomo de RCP. */
public class CprMusicPlayer {

  private static final String TAG = "CprMusicPlayer";

  private MediaPlayer mediaPlayer;
  private String loadedPath;
  private Uri loadedUri;
  private String trackLabel;
  private boolean pausedByUser;

  public boolean load(String absolutePath) {
    release();
    if (absolutePath == null || absolutePath.isEmpty()) {
      return false;
    }
    try {
      mediaPlayer = new MediaPlayer();
      mediaPlayer.setDataSource(absolutePath);
      preparePlayer();
      loadedPath = absolutePath;
      trackLabel = labelFromPath(absolutePath);
      pausedByUser = false;
      return true;
    } catch (IOException e) {
      Log.w(TAG, "Falha ao carregar mp3: " + absolutePath, e);
      release();
      return false;
    }
  }

  public boolean load(Context context, Uri uri, String displayName) {
    release();
    if (context == null || uri == null) {
      return false;
    }
    try {
      mediaPlayer = new MediaPlayer();
      mediaPlayer.setDataSource(context, uri);
      preparePlayer();
      loadedUri = uri;
      trackLabel = displayName == null || displayName.isEmpty() ? "Música selecionada" : displayName;
      pausedByUser = false;
      return true;
    } catch (IOException e) {
      Log.w(TAG, "Falha ao carregar URI: " + uri, e);
      release();
      return false;
    }
  }

  private void preparePlayer() throws IOException {
    mediaPlayer.setLooping(true);
    mediaPlayer.prepare();
  }

  public void play() {
    if (mediaPlayer == null) {
      return;
    }
    if (!mediaPlayer.isPlaying()) {
      mediaPlayer.start();
    }
    pausedByUser = false;
  }

  public void pause() {
    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
      mediaPlayer.pause();
    }
    pausedByUser = true;
  }

  public void togglePlayPause() {
    if (isPlaying()) {
      pause();
    } else {
      play();
    }
  }

  public void restart() {
    if (mediaPlayer == null) {
      return;
    }
    mediaPlayer.seekTo(0);
    play();
  }

  public boolean isPlaying() {
    return mediaPlayer != null && mediaPlayer.isPlaying();
  }

  public boolean isPausedByUser() {
    return pausedByUser;
  }

  public boolean isLoaded() {
    return mediaPlayer != null;
  }

  public String getLoadedPath() {
    return loadedPath;
  }

  public Uri getLoadedUri() {
    return loadedUri;
  }

  public String getTrackLabel() {
    if (trackLabel != null) {
      return trackLabel;
    }
    if (loadedPath == null) {
      return "Nenhuma música";
    }
    return labelFromPath(loadedPath);
  }

  private static String labelFromPath(String path) {
    int slash = path.lastIndexOf('/');
    if (slash >= 0 && slash < path.length() - 1) {
      return path.substring(slash + 1);
    }
    return path;
  }

  public void release() {
    if (mediaPlayer != null) {
      try {
        if (mediaPlayer.isPlaying()) {
          mediaPlayer.stop();
        }
      } catch (IllegalStateException ignored) {
        // MediaPlayer já liberado ou em estado inválido.
      }
      mediaPlayer.release();
      mediaPlayer = null;
    }
    loadedPath = null;
    loadedUri = null;
    trackLabel = null;
    pausedByUser = false;
  }
}
