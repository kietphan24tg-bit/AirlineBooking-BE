package com.example.moduleweb.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.exception.AppException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InMemoryRateLimitStoreTest {

  @Test
  void capacityDoesNotEvictLiveCountersAndReclaimsExpiredCounters() {
    AtomicLong now = new AtomicLong();
    InMemoryRateLimitStore store = new InMemoryRateLimitStore(now::get, 2);
    store.increment("first", Duration.ofSeconds(5));
    store.increment("second", Duration.ofSeconds(5));
    AppException error =
        assertThrows(AppException.class, () -> store.increment("third", Duration.ofSeconds(5)));
    assertEquals("RATE_LIMIT_CAPACITY_EXCEEDED", error.getCode());
    assertEquals(2L, store.increment("first", Duration.ofSeconds(5)));
    now.set(Duration.ofSeconds(5).toNanos());
    assertEquals(1L, store.increment("third", Duration.ofSeconds(5)));
    assertEquals(0L, store.getCount("first"));
  }

  @Test
  void expiresCounterAfterWindow() {
    AtomicLong now = new AtomicLong();
    InMemoryRateLimitStore store = new InMemoryRateLimitStore(now::get);

    assertEquals(1L, store.increment("query", Duration.ofSeconds(5)));
    assertEquals(2L, store.increment("query", Duration.ofSeconds(5)));

    now.set(Duration.ofSeconds(5).toNanos());

    assertEquals(0L, store.getCount("query"));
    assertEquals(1L, store.increment("query", Duration.ofSeconds(5)));
  }

  @Test
  void supportsAcquireAndRelease() {
    InMemoryRateLimitStore store = new InMemoryRateLimitStore();

    assertTrue(store.tryAcquire("slot", Duration.ofMinutes(1)));
    assertFalse(store.tryAcquire("slot", Duration.ofMinutes(1)));

    store.release("slot");

    assertTrue(store.tryAcquire("slot", Duration.ofMinutes(1)));
  }
}
