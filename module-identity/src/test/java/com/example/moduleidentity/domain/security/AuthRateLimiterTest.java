package com.example.moduleidentity.domain.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.common.exception.AppException;
import com.example.moduleidentity.config.RateLimitProperties;
import com.example.moduleidentity.port.RateLimitStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthRateLimiterTest {

  @Test
  void shouldBlockLoginAfterConfiguredFailedAttempts() {
    InMemoryRateLimitStore store = new InMemoryRateLimitStore();
    AuthRateLimiter throttleService = throttleService(store);

    for (int i = 0; i < 5; i++) {
      throttleService.recordLoginFailure("user@example.com", "ip-1");
    }

    AppException exception =
        assertThrows(
            AppException.class,
            () -> throttleService.checkLoginAllowed("user@example.com", "ip-1"));

    assertEquals("TOO_MANY_LOGIN_ATTEMPTS", exception.getCode());
  }

  @Test
  void shouldBlockRepeatedPasswordResetRequests() {
    InMemoryRateLimitStore store = new InMemoryRateLimitStore();
    AuthRateLimiter throttleService = throttleService(store);

    for (int i = 0; i < 3; i++) {
      throttleService.consumePasswordResetAttempt("user@example.com", "ip-1");
    }

    AppException exception =
        assertThrows(
            AppException.class,
            () -> throttleService.consumePasswordResetAttempt("user@example.com", "ip-1"));

    assertEquals("TOO_MANY_PASSWORD_RESET_REQUESTS", exception.getCode());
  }

  @Test
  void shouldBlockRefreshAttemptsByUserLimit() {
    InMemoryRateLimitStore store = new InMemoryRateLimitStore();
    AuthRateLimiter throttleService = throttleService(store);

    for (int i = 0; i < 20; i++) {
      throttleService.consumeRefreshUserAttempt(42L);
    }

    AppException exception =
        assertThrows(AppException.class, () -> throttleService.consumeRefreshUserAttempt(42L));

    assertEquals("TOO_MANY_REFRESH_ATTEMPTS", exception.getCode());
  }

  @Test
  void shouldFailOpenWhenStoreUnavailable() {
    AuthRateLimiter throttleService =
        throttleService(
            new RateLimitStore() {
              @Override
              public long increment(String key, Duration window) {
                throw new IllegalStateException("redis-down");
              }

              @Override
              public long getCount(String key) {
                throw new IllegalStateException("redis-down");
              }

              @Override
              public void delete(String key) {
                throw new IllegalStateException("redis-down");
              }
            });

    assertDoesNotThrow(
        () -> throttleService.consumePasswordResetAttempt("user@example.com", "ip-1"));
  }

  private static AuthRateLimiter throttleService(RateLimitStore store) {
    return new AuthRateLimiter(
        store, new RateLimitProperties(), new TokenHashingService(), new SimpleMeterRegistry());
  }

  private static final class InMemoryRateLimitStore implements RateLimitStore {
    private final Map<String, Long> counts = new HashMap<>();

    @Override
    public long increment(String key, Duration window) {
      long next = counts.getOrDefault(key, 0L) + 1L;
      counts.put(key, next);
      return next;
    }

    @Override
    public long getCount(String key) {
      return counts.getOrDefault(key, 0L);
    }

    @Override
    public void delete(String key) {
      counts.remove(key);
    }
  }
}
