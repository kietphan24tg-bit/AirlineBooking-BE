package com.example.moduleai.guardrail;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.moduleai.config.AiAgentProperties;
import com.example.moduleai.observability.AiObservability;
import com.example.moduleidentity.port.RateLimitStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentConcurrencyLimiterTest {

  @Mock private RateLimitStore rateLimitStore;
  @Mock private AiObservability observability;

  private AgentConcurrencyLimiter limiter;

  @BeforeEach
  void setUp() {
    limiter = new AgentConcurrencyLimiter(rateLimitStore, new AiAgentProperties(), observability);
  }

  @Test
  void releasesAcquiredSlotOnClose() {
    when(rateLimitStore.tryAcquire(contains("chat:user:42"), any())).thenReturn(true);

    try (AgentConcurrencyLimiter.Slot ignored = limiter.acquireUserSlot(42L, "chat")) {
      // slot is held for the request scope
    }

    verify(rateLimitStore).release(contains("chat:user:42"));
  }

  @Test
  void blocksWhenUserAlreadyHasRunningAction() {
    when(rateLimitStore.tryAcquire(contains("confirm:user:42"), any())).thenReturn(false);

    assertThatThrownBy(() -> limiter.acquireUserSlot(42L, "confirm"))
        .isInstanceOf(AppException.class)
        .satisfies(
            exception ->
                org.assertj.core.api.Assertions.assertThat(((AppException) exception).getCode())
                    .isEqualTo("AGENT_CONCURRENCY_LIMIT_EXCEEDED"));

    verify(observability).recordAgentRateLimitBlocked("concurrency", "confirm");
  }

  @Test
  void failsClosedWhenStoreIsUnavailable() {
    when(rateLimitStore.tryAcquire(any(), any()))
        .thenThrow(new IllegalStateException("Redis down"));

    assertThatThrownBy(() -> limiter.acquireUserSlot(42L, "chat"))
        .isInstanceOf(AppException.class)
        .satisfies(
            exception ->
                org.assertj.core.api.Assertions.assertThat(((AppException) exception).getCode())
                    .isEqualTo("AGENT_CONCURRENCY_UNAVAILABLE"));

    verify(observability).recordAgentRateLimitBackendFailure("concurrency");
  }

  @Test
  void rejectsChatBurstBeyondLocalCapacityAndReleasesCapacityOnClose() {
    when(rateLimitStore.tryAcquire(any(), any())).thenReturn(true);

    AgentConcurrencyLimiter.Slot first = limiter.acquireOwnerSlot("guest:1", "chat");
    AgentConcurrencyLimiter.Slot second = limiter.acquireOwnerSlot("guest:2", "chat");
    AgentConcurrencyLimiter.Slot third = limiter.acquireOwnerSlot("guest:3", "chat");

    assertThatThrownBy(() -> limiter.acquireOwnerSlot("guest:4", "chat"))
        .isInstanceOf(AppException.class)
        .satisfies(
            exception -> {
              AppException appException = (AppException) exception;
              org.assertj.core.api.Assertions.assertThat(appException.getCode())
                  .isEqualTo("AGENT_CHAT_CAPACITY_EXCEEDED");
              org.assertj.core.api.Assertions.assertThat(appException.getStatusCode())
                  .isEqualTo(503);
            });

    first.close();
    first.close();
    try (AgentConcurrencyLimiter.Slot ignored = limiter.acquireOwnerSlot("guest:4", "chat")) {
      assertThatThrownBy(() -> limiter.acquireOwnerSlot("guest:5", "chat"))
          .isInstanceOf(AppException.class)
          .satisfies(
              exception ->
                  org.assertj.core.api.Assertions.assertThat(((AppException) exception).getCode())
                      .isEqualTo("AGENT_CHAT_CAPACITY_EXCEEDED"));
    }
    second.close();
    third.close();

    verify(observability, times(2)).recordAgentRateLimitBlocked("capacity", "chat");
  }
}
