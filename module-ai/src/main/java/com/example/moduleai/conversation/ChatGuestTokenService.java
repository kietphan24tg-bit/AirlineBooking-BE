package com.example.moduleai.conversation;

import com.example.moduleai.config.AiChatProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class ChatGuestTokenService {

  private final AiChatProperties properties;
  private final SecureRandom secureRandom = new SecureRandom();

  public ChatGuestTokenService(AiChatProperties properties) {
    this.properties = properties;
  }

  public String resolveOrIssue(HttpServletRequest request, HttpServletResponse response) {
    String existing = readCookie(request);
    if (existing != null && !existing.isBlank()) {
      refreshCookie(response, existing, properties.getGuestCookieMaxAge());
      return existing;
    }
    byte[] raw = new byte[32];
    secureRandom.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    refreshCookie(response, token, properties.getGuestCookieMaxAge());
    return token;
  }

  public void clear(HttpServletResponse response) {
    refreshCookie(response, "", Duration.ZERO);
  }

  public String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        builder.append(String.format("%02x", value));
      }
      return builder.toString();
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to hash chat guest token", exception);
    }
  }

  private String readCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (properties.getGuestCookieName().equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private void refreshCookie(HttpServletResponse response, String token, Duration maxAge) {
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(properties.getGuestCookieName(), token)
            .httpOnly(properties.isGuestCookieHttpOnly())
            .secure(properties.isGuestCookieSecure())
            .path(properties.getGuestCookiePath())
            .maxAge(Math.max(0L, maxAge.getSeconds()))
            .sameSite(properties.getGuestCookieSameSite());
    if (properties.getGuestCookieDomain() != null && !properties.getGuestCookieDomain().isBlank()) {
      builder.domain(properties.getGuestCookieDomain());
    }
    response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
  }
}
