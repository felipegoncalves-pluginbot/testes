package com.felipe.elftemplate.logic;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

/** Abre o seletor de arquivos nativo do Android para escolher áudio (.mp3, etc.). */
public final class CprSystemAudioPicker {

  private static final String TAG = "CprSystemAudioPicker";

  public static final int REQUEST_PICK_AUDIO = 4201;

  private static final String[] AUDIO_MIME_TYPES = {
    "audio/mpeg",
    "audio/mp3",
    "audio/x-mpeg",
    "audio/mp4",
    "audio/*",
    "application/octet-stream"
  };

  private CprSystemAudioPicker() {}

  public static void launch(Activity activity, String chooserTitle) {
    if (activity == null) {
      return;
    }

    Intent openDocument = buildPickIntent(Intent.ACTION_OPEN_DOCUMENT);
    Intent getContent = buildPickIntent(Intent.ACTION_GET_CONTENT);

    String title = chooserTitle == null ? "Escolher música" : chooserTitle;
    Intent chooser = Intent.createChooser(openDocument, title);
    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {getContent});
    activity.startActivityForResult(chooser, REQUEST_PICK_AUDIO);
  }

  private static Intent buildPickIntent(String action) {
    Intent intent = new Intent(action);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    intent.putExtra(Intent.EXTRA_MIME_TYPES, AUDIO_MIME_TYPES);
    return intent;
  }

  public static void persistReadPermission(Context context, Uri uri, int intentFlags) {
    if (context == null || uri == null) {
      return;
    }
    int takeFlags = intentFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
    if (takeFlags == 0) {
      return;
    }
    try {
      context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
    } catch (SecurityException e) {
      Log.w(TAG, "Sem permissão persistível para URI (GET_CONTENT é ok nesta sessão)", e);
    }
  }

  public static String resolveDisplayName(Context context, Uri uri) {
    if (context == null || uri == null) {
      return "Música selecionada";
    }
    Cursor cursor = null;
    try {
      cursor = context.getContentResolver().query(uri, null, null, null, null);
      if (cursor != null && cursor.moveToFirst()) {
        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (nameIndex >= 0) {
          String name = cursor.getString(nameIndex);
          if (name != null && !name.isEmpty()) {
            return name;
          }
        }
      }
    } catch (RuntimeException e) {
      Log.w(TAG, "Falha ao ler nome do arquivo", e);
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    String last = uri.getLastPathSegment();
    if (last != null && !last.isEmpty()) {
      return last;
    }
    return "Música selecionada";
  }
}
