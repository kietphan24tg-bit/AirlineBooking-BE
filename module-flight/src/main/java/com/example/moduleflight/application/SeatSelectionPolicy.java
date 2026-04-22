package com.example.moduleflight.application;

import org.springframework.util.StringUtils;

public enum SeatSelectionPolicy {
  NOT_ALLOWED,
  PAID,
  INCLUDED,
  CHECKIN_ONLY;

  public static SeatSelectionPolicy fromValue(String value) {
    if (!StringUtils.hasText(value)) {
      return NOT_ALLOWED;
    }
    try {
      return SeatSelectionPolicy.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return NOT_ALLOWED;
    }
  }

  public boolean allowsAdvanceSelection() {
    return this == PAID || this == INCLUDED;
  }

  public boolean isIncluded() {
    return this == INCLUDED;
  }
}
