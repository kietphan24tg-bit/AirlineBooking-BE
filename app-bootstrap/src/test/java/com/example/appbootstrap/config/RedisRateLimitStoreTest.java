package com.example.appbootstrap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitStoreTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RedisRateLimitStore store;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    store = new RedisRateLimitStore(redisTemplate);
  }

  @Test
  void shouldIncrementAndSetExpiryForFirstKeyUse() {
    when(valueOperations.increment("rate-limit:test")).thenReturn(1L);

    long count = store.increment("rate-limit:test", Duration.ofMinutes(5));

    assertEquals(1L, count);
    verify(redisTemplate).expire("rate-limit:test", Duration.ofMinutes(5));
  }

  @Test
  void shouldReadCurrentCount() {
    when(valueOperations.get("rate-limit:test")).thenReturn("4");

    assertEquals(4L, store.getCount("rate-limit:test"));
  }
}
