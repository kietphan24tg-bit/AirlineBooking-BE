package com.example.moduleidentity.domain.security;

import com.example.common.exception.AppException;
import com.example.moduleidentity.config.RateLimitProperties;
import com.example.moduleidentity.port.RateLimitStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);

  private final RateLimitStore rateLimitStore;
  private final RateLimitProperties rateLimitProperties;
  private final TokenHashingService tokenHashingService;
  private final MeterRegistry meterRegistry;

  public AuthRateLimiter(
      RateLimitStore rateLimitStore,
      RateLimitProperties rateLimitProperties,
      TokenHashingService tokenHashingService,
      MeterRegistry meterRegistry) {
    this.rateLimitStore = rateLimitStore;
    this.rateLimitProperties = rateLimitProperties;
    this.tokenHashingService = tokenHashingService;
    this.meterRegistry = meterRegistry;
  }

  public void checkLoginAllowed(String email, String clientKey) {
    RateLimitProperties.AuthWindow rule = rateLimitProperties.getLogin();
    assertBelowLimit(
        "login",
        List.of(
            candidate("login", "email", email, rule.getWindow(), rule.getEmailLimit()),
            candidate("login", "client", clientKey, rule.getWindow(), rule.getClientLimit()),
            candidate(
                "login",
                "pair",
                composePair(email, clientKey),
                rule.getWindow(),
                rule.getPairLimit())),
        "TOO_MANY_LOGIN_ATTEMPTS",
        "Too many failed login attempts. Please try again later");
  }

  public void recordLoginFailure(String email, String clientKey) {
    RateLimitProperties.AuthWindow rule = rateLimitProperties.getLogin();
    consume(
        "login",
        List.of(
            candidate("login", "email", email, rule.getWindow(), rule.getEmailLimit()),
            candidate("login", "client", clientKey, rule.getWindow(), rule.getClientLimit()),
            candidate(
                "login",
                "pair",
                composePair(email, clientKey),
                rule.getWindow(),
                rule.getPairLimit())),
        "TOO_MANY_LOGIN_ATTEMPTS",
        "Too many failed login attempts. Please try again later");
  }

  public void resetLoginFailures(String email, String clientKey) {
    if (!rateLimitProperties.isEnabled()) {
      return;
    }
    safeDelete(key("login", "email", email));
    safeDelete(key("login", "client", clientKey));
    safeDelete(key("login", "pair", composePair(email, clientKey)));
  }

  public void consumePasswordResetAttempt(String email, String clientKey) {
    RateLimitProperties.AuthWindow rule = rateLimitProperties.getPasswordReset();
    consume(
        "password-reset",
        List.of(
            candidate(
                "password-reset", "email", email, rule.getWindow(), rule.getEmailLimit()),
            candidate(
                "password-reset", "client", clientKey, rule.getWindow(), rule.getClientLimit()),
            candidate(
                "password-reset",
                "pair",
                composePair(email, clientKey),
                rule.getWindow(),
                rule.getPairLimit())),
        "TOO_MANY_PASSWORD_RESET_REQUESTS",
        "Too many password reset requests. Please try again later");
  }

  public void consumeRefreshClientAttempt(String clientKey) {
    RateLimitProperties.RefreshWindow rule = rateLimitProperties.getRefresh();
    consume(
        "refresh",
        List.of(candidate("refresh", "client", clientKey, rule.getWindow(), rule.getClientLimit())),
        "TOO_MANY_REFRESH_ATTEMPTS",
        "Too many refresh attempts. Please try again later");
  }

  public void consumeRefreshUserAttempt(Long userId) {
    if (userId == null) {
      return;
    }
    RateLimitProperties.RefreshWindow rule = rateLimitProperties.getRefresh();
    consume(
        "refresh",
        List.of(
            candidate(
                "refresh", "user", String.valueOf(userId), rule.getWindow(), rule.getUserLimit())),
        "TOO_MANY_REFRESH_ATTEMPTS",
        "Too many refresh attempts. Please try again later");
  }

  private void assertBelowLimit(
      String action, List<LimitCandidate> candidates, String code, String message) {
    if (!rateLimitProperties.isEnabled()) {
      return;
    }

    for (LimitCandidate candidate : candidates) {
      long current = safeGetCount(candidate);
      if (current >= candidate.limit()) {
        recordBlocked(action, candidate, current);
        throw new AppException(code, message, 429);
      }
    }
  }

  private void consume(
      String action, List<LimitCandidate> candidates, String code, String message) {
    if (!rateLimitProperties.isEnabled()) {
      return;
    }

    assertBelowLimit(action, candidates, code, message);

    for (LimitCandidate candidate : candidates) {
      long current = safeIncrement(candidate);
      meterRegistry.counter("auth.rate_limit.consume", "action", action).increment();
      if (current > candidate.limit()) {
        recordBlocked(action, candidate, current);
        throw new AppException(code, message, 429);
      }
    }
  }

  private long safeGetCount(LimitCandidate candidate) {
    try {
      return rateLimitStore.getCount(candidate.key());
    } catch (RuntimeException ex) {
      return handleStoreFailure("read", candidate, ex);
    }
  }

  private long safeIncrement(LimitCandidate candidate) {
    try {
      return rateLimitStore.increment(candidate.key(), candidate.window());
    } catch (RuntimeException ex) {
      return handleStoreFailure("increment", candidate, ex);
    }
  }

  private void safeDelete(String key) {
    try {
      rateLimitStore.delete(key);
    } catch (RuntimeException ex) {
      handleStoreFailure("delete", new LimitCandidate(key, "reset", Duration.ZERO, 0, key), ex);
    }
  }

  private long handleStoreFailure(String operation, LimitCandidate candidate, RuntimeException ex) {
    if (rateLimitProperties.isFailOpen()) {
      log.warn(
          "Rate limit backend degraded: operation={}, dimension={}, keyHash={}, failOpen=true, reason={}",
          operation,
          candidate.dimension(),
          mask(candidate.rawIdentifier()),
          ex.getMessage());
      meterRegistry.counter("auth.rate_limit.degraded", "operation", operation).increment();
      return 0L;
    }
    throw ex;
  }

  private void recordBlocked(String action, LimitCandidate candidate, long current) {
    log.warn(
        "Rate limit blocked: action={}, dimension={}, keyHash={}, current={}, limit={}",
        action,
        candidate.dimension(),
        mask(candidate.rawIdentifier()),
        current,
        candidate.limit());
    meterRegistry.counter("auth.rate_limit.blocked", "action", action).increment();
  }

  private LimitCandidate candidate(
      String action, String dimension, String rawIdentifier, Duration window, int limit) {
    String normalizedIdentifier = normalize(rawIdentifier);
    return new LimitCandidate(
        key(action, dimension, normalizedIdentifier),
        dimension,
        window,
        limit,
        normalizedIdentifier);
  }

  private static String composePair(String left, String right) {
    return normalize(left) + "|" + normalize(right);
  }

  private static String key(String action, String dimension, String identifier) {
    return "rate-limit:" + action + ":" + dimension + ":" + normalize(identifier);
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return "anonymous";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private String mask(String rawIdentifier) {
    String hash = tokenHashingService.hashToken(rawIdentifier);
    return hash.substring(0, Math.min(12, hash.length()));
  }

  private record LimitCandidate(
      String key, String dimension, Duration window, int limit, String rawIdentifier) {}
}
