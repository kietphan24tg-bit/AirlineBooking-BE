package com.example.appbootstrap.application.flight;

import com.example.moduleflight.config.AirportMarketProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AirportGeoIpCountryResolver {

  private final AirportMarketProperties properties;

  public AirportGeoIpCountryResolver(AirportMarketProperties properties) {
    this.properties = properties;
  }

  public String resolveCountry() {
    var attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
      return null;
    }
    HttpServletRequest request = servletAttributes.getRequest();
    for (String headerName : properties.proxyCountryHeaders()) {
      String country = normalizeCountry(request.getHeader(headerName));
      if (StringUtils.hasText(country)) {
        return country;
      }
    }
    return null;
  }

  private String normalizeCountry(String raw) {
    if (!StringUtils.hasText(raw)) {
      return null;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    if (normalized.length() != 2 || "XX".equals(normalized)) {
      return null;
    }
    for (String isoCountry : Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2)) {
      if (isoCountry.equals(normalized)) {
        return normalized;
      }
    }
    return null;
  }
}
