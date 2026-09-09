package com.example.moduleweb.config;

import com.example.moduleidentity.port.RateLimitStore;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnProperty(
    prefix = "app.rate-limit",
    name = "redis-fallback-enabled",
    havingValue = "true")
public class RedisFallbackRateLimitStore implements RateLimitStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(RedisFallbackRateLimitStore.class);

  private final RedisRateLimitStore redisStore;
  private final RateLimitStore fallbackStore;
  private final AtomicBoolean redisAvailable = new AtomicBoolean(true);

  @Autowired
  public RedisFallbackRateLimitStore(RedisRateLimitStore redisStore) {
    this(redisStore, new InMemoryRateLimitStore());
  }

  RedisFallbackRateLimitStore(RedisRateLimitStore redisStore, RateLimitStore fallbackStore) {
    this.redisStore = redisStore;
    this.fallbackStore = fallbackStore;
  }

  @Override
  public long increment(String key, Duration window) {
    try {
      long count = redisStore.increment(key, window);
      fallbackStore.delete(key);
      recordRedisRecovered();
      return count;
    } catch (RuntimeException exception) {
      recordRedisFailure(exception);
      return fallbackStore.increment(key, window);
    }
  }

  @Override
  public long getCount(String key) {
    try {
      long count = redisStore.getCount(key);
      fallbackStore.delete(key);
      recordRedisRecovered();
      return count;
    } catch (RuntimeException exception) {
      recordRedisFailure(exception);
      return fallbackStore.getCount(key);
    }
  }

  @Override
  public void delete(String key) {
    fallbackStore.delete(key);
    try {
      redisStore.delete(key);
      recordRedisRecovered();
    } catch (RuntimeException exception) {
      recordRedisFailure(exception);
    }
  }

  private void recordRedisFailure(RuntimeException exception) {
    if (redisAvailable.compareAndSet(true, false)) {
      LOGGER.warn(
          "Redis rate-limit store unavailable; using process-local in-memory fallback", exception);
    }
  }

  private void recordRedisRecovered() {
    if (redisAvailable.compareAndSet(false, true)) {
      LOGGER.info("Redis rate-limit store recovered; in-memory fallback is no longer active");
    }
  }
}
