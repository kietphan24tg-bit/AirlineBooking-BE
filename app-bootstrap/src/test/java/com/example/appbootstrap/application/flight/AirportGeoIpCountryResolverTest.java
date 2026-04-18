package com.example.appbootstrap.application.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.moduleflight.config.AirportMarketProperties;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AirportGeoIpCountryResolverTest {

  private final AirportGeoIpCountryResolver resolver =
      new AirportGeoIpCountryResolver(
          new AirportMarketProperties("VN", List.of("CF-IPCountry", "X-App-Country")));

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void resolveCountry_readsFirstValidConfiguredHeader() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("CF-IPCountry", "ca");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    assertEquals("CA", resolver.resolveCountry());
  }

  @Test
  void resolveCountry_ignoresMalformedAndUnknownHeaders() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("CF-IPCountry", "XX");
    request.addHeader("X-App-Country", "bad");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    assertNull(resolver.resolveCountry());
  }
}
