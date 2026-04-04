package com.example.appbootstrap.config;

import com.example.moduleidentity.port.RateLimitStore;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisRateLimitStore implements RateLimitStore {

  private final StringRedisTemplate redisTemplate;

  public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public long increment(String key, Duration window) {
    Long current = redisTemplate.opsForValue().increment(key);
    if (current == null) {
      return 0L;
    }
    if (current == 1L) {
      redisTemplate.expire(key, window);
    }
    return current;
  }

  @Override
  public long getCount(String key) {
    String value = redisTemplate.opsForValue().get(key);
    if (value == null || value.isBlank()) {
      return 0L;
    }
    return Long.parseLong(value);
  }

  @Override
  public void delete(String key) {
    redisTemplate.delete(key);
  }
}
