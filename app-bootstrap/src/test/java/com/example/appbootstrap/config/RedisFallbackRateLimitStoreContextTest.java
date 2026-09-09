package com.example.moduleweb.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.example.appbootstrap.AppBootstrapApplication;
import com.example.moduleidentity.port.RateLimitStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = AppBootstrapApplication.class,
    properties = "app.rate-limit.redis-fallback-enabled=true")
class RedisFallbackRateLimitStoreContextTest {

  @Autowired private RateLimitStore rateLimitStore;

  @Test
  void selectsFallbackStoreWhenEnabled() {
    assertInstanceOf(RedisFallbackRateLimitStore.class, rateLimitStore);
  }
}
