package com.example.moduleidentity.port;

import java.time.Duration;

public interface RateLimitStore {

  long increment(String key, Duration window);

  long getCount(String key);

  void delete(String key);

  default boolean tryAcquire(String key, Duration ttl) {
    return increment(key, ttl) == 1L;
  }

  default void release(String key) {
    delete(key);
  }
}
