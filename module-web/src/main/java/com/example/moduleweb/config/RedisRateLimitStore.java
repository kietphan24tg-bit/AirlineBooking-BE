package com.example.moduleweb.config;

import com.example.moduleidentity.port.RateLimitStore;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisRateLimitStore implements RateLimitStore {

  private static final String INCR_WITH_TTL_LUA =
      "local current = redis.call('INCR', KEYS[1]) "
          + "if current == 1 then "
          + "redis.call('EXPIRE', KEYS[1], ARGV[1]) "
          + "end "
          + "return current";

  private static final DefaultRedisScript<Long> INCR_WITH_TTL_SCRIPT =
      new DefaultRedisScript<>(INCR_WITH_TTL_LUA, Long.class);

  private final StringRedisTemplate redisTemplate;

  public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public long increment(String key, Duration window) {
    long windowSeconds = Math.max(1L, window.getSeconds());
    Long current =
        redisTemplate.execute(INCR_WITH_TTL_SCRIPT, List.of(key), String.valueOf(windowSeconds));
    if (current == null) {
      return 0L;
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
