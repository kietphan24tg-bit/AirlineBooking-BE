package com.example.moduleidentity.port;

import java.time.Duration;

public interface RateLimitStore {

  long increment(String key, Duration window);

  long getCount(String key);

  void delete(String key);
}
