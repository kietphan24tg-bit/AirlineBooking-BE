package com.example.moduleidentity.domain.security;

import com.example.moduleidentity.port.ClientKeyResolver;

public final class ClientKeyUtils {

  private ClientKeyUtils() {}

  public static String normalizeClientKey(
      String clientKey, ClientKeyResolver clientKeyResolver, String fallbackValue) {
    String explicitClientKey = trimToNull(clientKey);
    if (explicitClientKey != null) {
      return explicitClientKey;
    }

    String resolvedClientKey = trimToNull(clientKeyResolver.resolveClientKey());
    return resolvedClientKey == null ? fallbackValue : resolvedClientKey;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
