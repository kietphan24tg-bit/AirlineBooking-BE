package com.example.moduleweb.config;

import com.example.common.exception.AppException;
import com.example.moduleidentity.port.RateLimitStore;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

final class InMemoryRateLimitStore implements RateLimitStore {

  private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
  private final LongSupplier nanoTime;
  private final int maxEntries;

  InMemoryRateLimitStore() {
    this(System::nanoTime);
  }

  InMemoryRateLimitStore(LongSupplier nanoTime) {
    this(nanoTime, 10_000);
  }

  InMemoryRateLimitStore(LongSupplier nanoTime, int maxEntries) {
    if (maxEntries < 1) {
      throw new IllegalArgumentException("maxEntries must be positive");
    }
    this.nanoTime = nanoTime;
    this.maxEntries = maxEntries;
  }

  @Override
  public synchronized long increment(String key, Duration window) {
    long now = nanoTime.getAsLong();
    if (!counters.containsKey(key) && counters.size() >= maxEntries) {
      counters.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() <= now);
      if (counters.size() >= maxEntries) {
        throw new AppException(
            "RATE_LIMIT_CAPACITY_EXCEEDED",
            "Rate limiter is at capacity. Please try again later.",
            429);
      }
    }
    long expiresAt = expiresAt(now, window);
    Counter counter =
        counters.compute(
            key,
            (ignored, current) -> {
              if (current == null || current.expiresAtNanos() <= now) {
                return new Counter(1L, expiresAt);
              }
              long nextCount =
                  current.count() == Long.MAX_VALUE ? Long.MAX_VALUE : current.count() + 1L;
              return new Counter(nextCount, current.expiresAtNanos());
            });
    return counter.count();
  }

  @Override
  public long getCount(String key) {
    long now = nanoTime.getAsLong();
    Counter counter = counters.get(key);
    if (counter == null) {
      return 0L;
    }
    if (counter.expiresAtNanos() <= now) {
      counters.remove(key, counter);
      return 0L;
    }
    return counter.count();
  }

  @Override
  public void delete(String key) {
    counters.remove(key);
  }

  private static long expiresAt(long now, Duration window) {
    long ttlNanos;
    try {
      ttlNanos = Math.max(1L, window.toNanos());
    } catch (ArithmeticException exception) {
      return Long.MAX_VALUE;
    }
    try {
      return Math.addExact(now, ttlNanos);
    } catch (ArithmeticException exception) {
      return Long.MAX_VALUE;
    }
  }

  private record Counter(long count, long expiresAtNanos) {}
}
