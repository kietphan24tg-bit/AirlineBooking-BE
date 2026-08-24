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
class AgentRequestLimiterTest {

  @Mock private RateLimitStore rateLimitStore;
  @Mock private AiObservability observability;

  private AgentRequestLimiter limiter;

  @BeforeEach
  void setUp() {
    AiAgentProperties properties = new AiAgentProperties();
    properties.setQueryRateLimitPerIp(2);
    properties.setQueryRateLimitPerUser(2);
    properties.setConfirmationRateLimitPerUser(1);
    limiter = new AgentRequestLimiter(rateLimitStore, properties, observability);
  }

  @Test
  void queryFailsOpenWhenRateLimitStoreIsUnavailable() {
    when(rateLimitStore.increment(any(), any())).thenThrow(new IllegalStateException("Redis down"));

    limiter.checkQuery(42L, "127.0.0.1");

    verify(observability, times(2)).recordAgentRateLimitBackendFailure("query");
  }

  @Test
  void confirmationFailsClosedWhenRateLimitStoreIsUnavailable() {
    when(rateLimitStore.increment(any(), any())).thenThrow(new IllegalStateException("Redis down"));

    assertThatThrownBy(() -> limiter.checkConfirmation(42L))
        .isInstanceOf(AppException.class)
        .satisfies(
            exception ->
                org.assertj.core.api.Assertions.assertThat(((AppException) exception).getCode())
                    .isEqualTo("AGENT_RATE_LIMIT_UNAVAILABLE"));
  }

  @Test
  void blocksConfirmationAboveConfiguredLimit() {
    when(rateLimitStore.increment(contains("confirm:user:42"), any())).thenReturn(2L);

    assertThatThrownBy(() -> limiter.checkConfirmation(42L))
        .isInstanceOf(AppException.class)
        .satisfies(
            exception ->
                org.assertj.core.api.Assertions.assertThat(((AppException) exception).getCode())
                    .isEqualTo("AGENT_RATE_LIMIT_EXCEEDED"));

    verify(observability).recordAgentRateLimitBlocked("confirm", "user");
  }
}
