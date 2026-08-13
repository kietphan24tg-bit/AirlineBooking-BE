package com.example.moduleweb.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class HttpRequestClientKeyResolverTest {

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldUseForwardedHeaderWhenRequestComesFromTrustedProxy() {
    HttpRequestClientKeyResolver resolver =
        new HttpRequestClientKeyResolver(new ClientIpProperties(java.util.List.of("10.0.0.0/8")));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.15");
    request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.15");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    assertEquals("203.0.113.10", resolver.resolveClientKey());
  }

  @Test
  void shouldIgnoreForwardedHeadersWhenRemoteAddressIsNotTrustedProxy() {
    HttpRequestClientKeyResolver resolver =
        new HttpRequestClientKeyResolver(new ClientIpProperties(java.util.List.of("10.0.0.0/8")));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("198.51.100.25");
    request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.15");
    request.addHeader("X-Real-IP", "203.0.113.200");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    assertEquals("198.51.100.25", resolver.resolveClientKey());
  }
}
