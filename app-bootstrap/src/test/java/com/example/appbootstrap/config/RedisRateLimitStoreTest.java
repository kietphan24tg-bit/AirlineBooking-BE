package com.example.moduleweb.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitStoreTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RedisRateLimitStore store;

  @BeforeEach
  void setUp() {
    store = new RedisRateLimitStore(redisTemplate);
  }

  @Test
  void shouldIncrementAndSetExpiryForFirstKeyUse() {
    when(redisTemplate.execute(
            org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
            eq(List.of("rate-limit:test")),
            eq("300")))
        .thenReturn(1L);

    long count = store.increment("rate-limit:test", Duration.ofMinutes(5));

    assertEquals(1L, count);
    verify(redisTemplate)
        .execute(
            org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
            eq(List.of("rate-limit:test")),
            eq("300"));
  }

  @Test
  void shouldReadCurrentCount() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("rate-limit:test")).thenReturn("4");

    assertEquals(4L, store.getCount("rate-limit:test"));
  }
}
