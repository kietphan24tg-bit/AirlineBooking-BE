package com.example.moduleai.guardrail;

import com.example.common.exception.AppException;
import com.example.moduleai.config.AiAgentProperties;
import com.example.moduleai.observability.AiObservability;
import com.example.moduleidentity.port.RateLimitStore;
import org.springframework.stereotype.Component;

@Component
public class AgentConcurrencyLimiter {

  private final RateLimitStore rateLimitStore;
  private final AiAgentProperties properties;
  private final AiObservability observability;

  public AgentConcurrencyLimiter(
      RateLimitStore rateLimitStore, AiAgentProperties properties, AiObservability observability) {
    this.rateLimitStore = rateLimitStore;
    this.properties = properties;
    this.observability = observability;
  }

  public Slot acquireUserSlot(Long userId, String action) {
    if (userId == null) {
      return Slot.noop();
    }
    return acquireSlot(key("user:" + userId, action), action);
  }

  public Slot acquireOwnerSlot(String ownerKey, String action) {
    if (ownerKey == null || ownerKey.isBlank()) {
      return Slot.noop();
    }
    return acquireSlot(key(ownerKey, action), action);
  }

  private Slot acquireSlot(String key, String action) {
    try {
      if (!rateLimitStore.tryAcquire(key, properties.getConcurrencySlotTtl())) {
        observability.recordAgentRateLimitBlocked("concurrency", action);
        throw new AppException(
            "AGENT_CONCURRENCY_LIMIT_EXCEEDED",
            "Another Agent action is already running for this account.",
            429);
      }
      return new Slot(rateLimitStore, observability, key, action, true);
    } catch (AppException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      observability.recordAgentRateLimitBackendFailure("concurrency");
      if (!properties.isConcurrencyFailOpen()) {
        throw new AppException(
            "AGENT_CONCURRENCY_UNAVAILABLE",
            "Agent concurrency control is temporarily unavailable.",
            503);
      }
      return Slot.noop();
    }
  }

  private static String key(String ownerKey, String action) {
    return "concurrency:agent:" + action + ":" + ownerKey;
  }

  public static final class Slot implements AutoCloseable {

    private final RateLimitStore rateLimitStore;
    private final AiObservability observability;
    private final String key;
    private final String action;
    private final boolean acquired;

    private Slot(
        RateLimitStore rateLimitStore,
        AiObservability observability,
        String key,
        String action,
        boolean acquired) {
      this.rateLimitStore = rateLimitStore;
      this.observability = observability;
      this.key = key;
      this.action = action;
      this.acquired = acquired;
    }

    private static Slot noop() {
      return new Slot(null, null, null, null, false);
    }

    @Override
    public void close() {
      if (!acquired) {
        return;
      }
      try {
        rateLimitStore.release(key);
      } catch (RuntimeException exception) {
        observability.recordAgentRateLimitBackendFailure("concurrency-release:" + action);
      }
    }
  }
}
