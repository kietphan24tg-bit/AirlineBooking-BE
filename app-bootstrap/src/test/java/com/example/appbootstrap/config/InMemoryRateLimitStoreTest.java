package com.example.moduleweb.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InMemoryRateLimitStoreTest {

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
