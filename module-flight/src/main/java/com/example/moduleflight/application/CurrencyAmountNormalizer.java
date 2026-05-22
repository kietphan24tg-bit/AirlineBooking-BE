package com.example.moduleflight.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CurrencyAmountNormalizer {

  private static final String VND = "VND";
  private static final int ZERO_DECIMAL_SCALE = 0;
  private static final int DEFAULT_SCALE = 2;

  private CurrencyAmountNormalizer() {}

  public static BigDecimal normalize(BigDecimal value, String currency) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    return value.setScale(scaleFor(currency), RoundingMode.HALF_UP);
  }

  public static BigDecimal normalizeOrZero(BigDecimal value, String currency) {
    if (value == null) {
      return BigDecimal.ZERO.setScale(scaleFor(currency), RoundingMode.HALF_UP);
    }
    return normalize(value, currency);
  }

  public static int scaleFor(String currency) {
    return isZeroDecimalCurrency(currency) ? ZERO_DECIMAL_SCALE : DEFAULT_SCALE;
  }

  public static boolean isZeroDecimalCurrency(String currency) {
    return currency != null && VND.equalsIgnoreCase(currency.trim());
  }
}
