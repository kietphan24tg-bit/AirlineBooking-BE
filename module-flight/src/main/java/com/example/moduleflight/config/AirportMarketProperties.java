package com.example.moduleflight.config;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.airport-market")
public record AirportMarketProperties(
    String defaultMarket, List<String> proxyCountryHeaders, Integer geoipScore) {

  private static final String DEFAULT_MARKET = "VN";
  private static final int DEFAULT_GEOIP_SCORE = 60;
  private static final List<String> DEFAULT_PROXY_COUNTRY_HEADERS =
      List.of("CF-IPCountry", "X-Vercel-IP-Country", "CloudFront-Viewer-Country", "X-App-Country");

  public AirportMarketProperties {
    defaultMarket = normalizeMarket(defaultMarket);
    proxyCountryHeaders =
        proxyCountryHeaders == null || proxyCountryHeaders.isEmpty()
            ? DEFAULT_PROXY_COUNTRY_HEADERS
            : List.copyOf(proxyCountryHeaders);
    geoipScore = geoipScore == null || geoipScore < 0 ? DEFAULT_GEOIP_SCORE : geoipScore;
  }

  private static String normalizeMarket(String raw) {
    if (!StringUtils.hasText(raw)) {
      return DEFAULT_MARKET;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    if (normalized.length() != 2 || "XX".equals(normalized)) {
      return DEFAULT_MARKET;
    }
    for (String isoCountry : Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2)) {
      if (isoCountry.equals(normalized)) {
        return normalized;
      }
    }
    return DEFAULT_MARKET;
  }
}
