package com.felipe.elftemplate.logic;

import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Localiza arquivos .mp3 no armazenamento do Sanbot (Bluetooth, Download, Music, etc.). */
public final class CprMusicFinder {

  private static final int MAX_RESULTS = 48;
  private static final int MAX_DEPTH = 6;

  /** Pastas comuns no Android/Sanbot — Bluetooth primeiro (arquivos enviados por celular). */
  static final String[] PRIORITY_FOLDERS = {
    "Bluetooth",
    "bluetooth",
    "Download",
    "Downloads",
    "Music",
    "sanbot",
    "elf",
    "Documents",
    "Ringtones",
    "Notifications",
    "DCIM"
  };

  private CprMusicFinder() {}

  public static List<String> findMp3Files() {
    List<String> found = new ArrayList<String>();
    List<File> roots = buildSearchRoots();
    for (int i = 0; i < roots.size(); i++) {
      File root = roots.get(i);
      if (root != null && root.exists()) {
        scanDirectory(root, 0, found);
      }
      if (found.size() >= MAX_RESULTS) {
        break;
      }
    }
    Collections.sort(found, MP3_PRIORITY);
    return found;
  }

  public static String pickBestMp3(List<String> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }
    List<String> ranked = new ArrayList<String>(candidates);
    Collections.sort(ranked, MP3_PRIORITY);
    return ranked.get(0);
  }

  private static List<File> buildSearchRoots() {
    List<File> roots = new ArrayList<File>();
    addStorageRoots(roots, Environment.getExternalStorageDirectory());
    addStorageRoots(roots, new File("/sdcard"));
    addStorageRoots(roots, new File("/storage/emulated/0"));
    addStorageRoots(roots, new File("/storage/sdcard0"));
    return roots;
  }

  private static void addStorageRoots(List<File> roots, File base) {
    if (base == null || !base.exists()) {
      return;
    }
    for (int i = 0; i < PRIORITY_FOLDERS.length; i++) {
      File folder = new File(base, PRIORITY_FOLDERS[i]);
      if (folder.exists() && !roots.contains(folder)) {
        roots.add(folder);
      }
    }
    if (!roots.contains(base)) {
      roots.add(base);
    }
  }

  private static void scanDirectory(File dir, int depth, List<String> found) {
    if (dir == null || !dir.isDirectory() || depth > MAX_DEPTH || found.size() >= MAX_RESULTS) {
      return;
    }
    File[] children = dir.listFiles();
    if (children == null) {
      return;
    }
    for (int i = 0; i < children.length; i++) {
      File child = children[i];
      if (child.isFile() && child.getName().toLowerCase(Locale.US).endsWith(".mp3")) {
        found.add(child.getAbsolutePath());
        if (found.size() >= MAX_RESULTS) {
          return;
        }
      }
    }
    for (int i = 0; i < children.length; i++) {
      File child = children[i];
      if (child.isDirectory() && !child.getName().startsWith(".")) {
        scanDirectory(child, depth + 1, found);
        if (found.size() >= MAX_RESULTS) {
          return;
        }
      }
    }
  }

  private static final Comparator<String> MP3_PRIORITY =
      new Comparator<String>() {
        @Override
        public int compare(String a, String b) {
          return Integer.compare(score(b), score(a));
        }

        private int score(String path) {
          String lower = path.toLowerCase(Locale.US);
          int s = 0;
          if (lower.contains("rcp")) {
            s += 100;
          }
          if (lower.contains("cpr")) {
            s += 100;
          }
          if (lower.contains("ressuscit")) {
            s += 80;
          }
          if (lower.contains("stayin")) {
            s += 60;
          }
          if (lower.contains("metronom")) {
            s += 50;
          }
          if (lower.contains("bluetooth")) {
            s += 40;
          }
          if (lower.contains("music")) {
            s += 10;
          }
          return s;
        }
      };
}
