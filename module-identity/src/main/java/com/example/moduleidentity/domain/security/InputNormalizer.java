package com.example.moduleidentity.domain.security;

import com.example.common.exception.AppException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class InputNormalizer {

  public String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public String trimRequired(String value, String errorCode, String message) {
    String normalized = blankToNull(value);
    if (normalized == null) {
      throw new AppException(errorCode, message, 400);
    }
    return normalized;
  }

  public String trimIfPresentNonBlank(String value, String errorCode, String message) {
    if (value == null) {
      return null;
    }
    String normalized = blankToNull(value);
    if (normalized == null) {
      throw new AppException(errorCode, message, 400);
    }
    return normalized;
  }

  public String normalizeNullableString(String value) {
    return value == null ? null : blankToNull(value);
  }

  public String normalizeOptionalCountryCode(String nationality) {
    if (nationality == null) {
      return null;
    }
    String normalizedCountryCode = blankToNull(nationality);
    if (normalizedCountryCode == null) {
      return null;
    }
    String region = normalizedCountryCode.toUpperCase(Locale.ROOT);
    if (region.length() != 2 || !region.chars().allMatch(Character::isLetter)) {
      throw new AppException("INVALID_COUNTRY_CODE", "nationality must be ISO alpha-2", 400);
    }
    return region;
  }
}
