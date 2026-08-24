package com.example.appbootstrap.config;

import com.example.moduleai.resilience.AiProviderGuard;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("aiProvider")
public class AiProviderHealthIndicator implements HealthIndicator {

  private final AiProviderGuard providerGuard;

  public AiProviderHealthIndicator(AiProviderGuard providerGuard) {
    this.providerGuard = providerGuard;
  }

  @Override
  public Health health() {
    Health.Builder health = providerGuard.isAvailable() ? Health.up() : Health.outOfService();
    return health
        .withDetail("chatCircuitState", providerGuard.circuitState())
        .withDetail("retrievalCircuitState", providerGuard.retrievalCircuitState())
        .build();
  }
}
