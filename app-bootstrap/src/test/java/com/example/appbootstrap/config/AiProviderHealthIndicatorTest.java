package com.example.appbootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.moduleai.resilience.AiProviderGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class AiProviderHealthIndicatorTest {

  @Test
  void reportsUpWhenBothCircuitsAreAvailable() {
    AiProviderGuard guard = mock(AiProviderGuard.class);
    when(guard.isAvailable()).thenReturn(true);
    when(guard.circuitState()).thenReturn("CLOSED");
    when(guard.retrievalCircuitState()).thenReturn("CLOSED");

    var health = new AiProviderHealthIndicator(guard).health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("chatCircuitState", "CLOSED")
        .containsEntry("retrievalCircuitState", "CLOSED");
  }

  @Test
  void reportsOutOfServiceWhenACircuitIsOpen() {
    AiProviderGuard guard = mock(AiProviderGuard.class);
    when(guard.isAvailable()).thenReturn(false);
    when(guard.circuitState()).thenReturn("OPEN");
    when(guard.retrievalCircuitState()).thenReturn("CLOSED");

    var health = new AiProviderHealthIndicator(guard).health();

    assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
  }
}
