package com.example.moduleflight.application;

import java.time.LocalTime;

/**
 * Local time-of-day buckets for flight search. ANY means no filtering.
 *
 * <ul>
 *   <li>MORNING: 00:00:00–11:59:59
 *   <li>AFTERNOON: 12:00:00–17:59:59
 *   <li>EVENING: 18:00:00–23:59:59
 * </ul>
 */
public enum TimeOfDayBucket {
  ANY,
  MORNING,
  AFTERNOON,
  EVENING;

  public boolean matches(LocalTime t) {
    if (this == ANY) {
      return true;
    }
    int h = t.getHour();
    return switch (this) {
      case MORNING -> h < 12;
      case AFTERNOON -> h >= 12 && h < 18;
      case EVENING -> h >= 18;
      case ANY -> true;
    };
  }
}
