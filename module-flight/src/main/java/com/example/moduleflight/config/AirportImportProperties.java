package com.example.moduleflight.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.airport-import")
public record AirportImportProperties(
    boolean enabled,
    String baseUrl,
    String cron,
    int pageSize,
    int requestTimeoutSeconds,
    int maxRetries,
    String allowedTypes,
    boolean requireIata,
    String include,
    String fallbackCity,
    String fallbackCountry,
    String fallbackTimeZone) {

  public AirportImportProperties {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("app.airport-import.page-size must be greater than 0");
    }
    if (requestTimeoutSeconds <= 0) {
      throw new IllegalArgumentException(
          "app.airport-import.request-timeout-seconds must be greater than 0");
    }
    if (maxRetries < 0) {
      throw new IllegalArgumentException("app.airport-import.max-retries must be greater than or equal to 0");
    }
  }
}
