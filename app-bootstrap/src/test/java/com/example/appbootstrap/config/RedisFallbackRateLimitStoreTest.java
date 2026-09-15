package com.example.moduleweb.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleidentity.port.RateLimitStore;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedisFallbackRateLimitStoreTest {

  @Mock private RedisRateLimitStore redisStore;

  private RateLimitStore fallbackStore;
  private RedisFallbackRateLimitStore store;

  @BeforeEach
  void setUp() {
    fallbackStore = new InMemoryRateLimitStore();
    store = new RedisFallbackRateLimitStore(redisStore, fallbackStore);
  }

  @Test
  void usesRedisWhenAvailable() {
    when(redisStore.increment("query", Duration.ofMinutes(1))).thenReturn(3L);

    assertEquals(3L, store.increment("query", Duration.ofMinutes(1)));
  }

  @Test
  void fallsBackToMemoryWhenRedisIncrementFails() {
    when(redisStore.increment("query", Duration.ofMinutes(1)))
        .thenThrow(new IllegalStateException("Redis unavailable"));

    assertEquals(1L, store.increment("query", Duration.ofMinutes(1)));
    assertEquals(2L, store.increment("query", Duration.ofMinutes(1)));
  }

  @Test
  void fallsBackToMemoryWhenRedisReadFails() {
    fallbackStore.increment("query", Duration.ofMinutes(1));
    when(redisStore.getCount("query")).thenThrow(new IllegalStateException("Redis unavailable"));

    assertEquals(1L, store.getCount("query"));
  }

  @Test
  void clearsStaleFallbackCounterAfterRedisRecovers() {
    when(redisStore.increment("query", Duration.ofMinutes(1)))
        .thenThrow(new IllegalStateException("Redis unavailable"))
        .thenReturn(4L)
        .thenThrow(new IllegalStateException("Redis unavailable again"));

    assertEquals(1L, store.increment("query", Duration.ofMinutes(1)));
    assertEquals(4L, store.increment("query", Duration.ofMinutes(1)));
    assertEquals(1L, store.increment("query", Duration.ofMinutes(1)));
  }

  @Test
  void releaseAlwaysClearsMemoryWhenRedisFails() {
    fallbackStore.increment("slot", Duration.ofMinutes(1));
    doThrow(new IllegalStateException("Redis unavailable")).when(redisStore).delete("slot");

    store.delete("slot");

    assertEquals(0L, fallbackStore.getCount("slot"));
    verify(redisStore).delete("slot");
  }
}
