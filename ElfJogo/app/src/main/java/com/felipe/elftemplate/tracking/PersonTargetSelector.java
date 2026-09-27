package com.felipe.elftemplate.tracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Seleciona jogador(es) principal conforme modo Kinect-style (closest / sticky / center). */
public class PersonTargetSelector {

  /** Comparadores explícitos — evita lambdas (NoClassDefFoundError em Sanbot/API 21). */
  private static final Comparator<PersonBlob> BY_DISTANCE_Z =
      new Comparator<PersonBlob>() {
        @Override
        public int compare(PersonBlob a, PersonBlob b) {
          return Integer.compare(a.distanceZ, b.distanceZ);
        }
      };

  private static final Comparator<PersonBlob> BY_CENTER_X =
      new Comparator<PersonBlob>() {
        @Override
        public int compare(PersonBlob a, PersonBlob b) {
          float da = Math.abs(a.centroidX - 0.5f);
          float db = Math.abs(b.centroidX - 0.5f);
          return Float.compare(da, db);
        }
      };

  public PersonBlob selectPrimary(List<PersonBlob> blobs, TrackingModeConfig config) {
    if (blobs == null || blobs.isEmpty()) {
      config.updateStickyLock(-1);
      return null;
    }

    List<PersonBlob> sorted = sortForMode(blobs, config.getMode());
    PersonBlob primary = pickPrimary(sorted, config);
    if (primary != null) {
      config.updateStickyLock(primary.label);
    } else {
      config.updateStickyLock(-1);
    }
    return primary;
  }

  public List<PersonBlob> selectTracked(List<PersonBlob> blobs, TrackingModeConfig config) {
    if (blobs == null || blobs.isEmpty()) {
      return Collections.emptyList();
    }
    List<PersonBlob> sorted = sortForMode(blobs, config.getMode());
    int limit = limitForMode(config);
    if (sorted.size() <= limit) {
      return sorted;
    }
    return new ArrayList<>(sorted.subList(0, limit));
  }

  private int limitForMode(TrackingModeConfig config) {
    switch (config.getMode()) {
      case MULTI_CLOSEST_TWO:
        return 2;
      case MULTI_ALL:
        return config.getMaxPersons();
      default:
        return 1;
    }
  }

  private List<PersonBlob> sortForMode(List<PersonBlob> blobs, TrackingMode mode) {
    List<PersonBlob> copy = new ArrayList<>(blobs);
    if (mode == TrackingMode.SINGLE_CENTER) {
      Collections.sort(copy, BY_CENTER_X);
    } else {
      Collections.sort(copy, BY_DISTANCE_Z);
    }
    return copy;
  }

  private PersonBlob pickPrimary(List<PersonBlob> sorted, TrackingModeConfig config) {
    if (sorted.isEmpty()) {
      return null;
    }
    if (config.getMode() == TrackingMode.SINGLE_STICKY && config.getStickyBlobLabel() >= 0) {
      for (PersonBlob blob : sorted) {
        if (blob.label == config.getStickyBlobLabel()) {
          return blob;
        }
      }
    }
    PersonBlob closest = sorted.get(0);
    int n = Math.min(3, sorted.size());
    PersonBlob primary = closest;
    for (int i = 1; i < n; i++) {
      PersonBlob candidate = sorted.get(i);
      boolean clearlyLarger = candidate.pixelCount > primary.pixelCount * 2;
      boolean largerNearby =
          candidate.pixelCount > primary.pixelCount * 3 / 2
              && candidate.distanceZ <= closest.distanceZ + 800;
      if (clearlyLarger || largerNearby) {
        primary = candidate;
      }
    }
    return primary;
  }
}
