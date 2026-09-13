package com.example.moduleai.guardrail;

import com.example.common.exception.AppException;
import com.example.moduleai.config.AiAgentProperties;
import com.example.moduleai.observability.AiObservability;
import com.example.moduleidentity.port.RateLimitStore;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class AgentConcurrencyLimiter {

  private final RateLimitStore rateLimitStore;
  private final AiAgentProperties properties;
  private final AiObservability observability;
  private final Semaphore chatTurnSlots;

  public AgentConcurrencyLimiter(
      RateLimitStore rateLimitStore, AiAgentProperties properties, AiObservability observability) {
    this.rateLimitStore = rateLimitStore;
    this.properties = properties;
    this.observability = observability;
    this.chatTurnSlots = new Semaphore(properties.getMaxConcurrentChatTurns(), true);
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
    Semaphore localSlot = acquireLocalSlot(action);
    try {
      if (!rateLimitStore.tryAcquire(key, properties.getConcurrencySlotTtl())) {
        observability.recordAgentRateLimitBlocked("concurrency", action);
        throw new AppException(
            "AGENT_CONCURRENCY_LIMIT_EXCEEDED",
            "Another Agent action is already running for this account.",
            429);
      }
      return new Slot(rateLimitStore, observability, key, action, true, localSlot);
    } catch (AppException exception) {
      releaseLocalSlot(localSlot);
      throw exception;
    } catch (RuntimeException exception) {
      observability.recordAgentRateLimitBackendFailure("concurrency");
      if (!properties.isConcurrencyFailOpen()) {
        releaseLocalSlot(localSlot);
        throw new AppException(
            "AGENT_CONCURRENCY_UNAVAILABLE",
            "Agent concurrency control is temporarily unavailable.",
            503);
      }
      return new Slot(null, observability, null, action, false, localSlot);
    }
  }

  private Semaphore acquireLocalSlot(String action) {
    if (!"chat".equals(action)) {
      return null;
    }
    if (!chatTurnSlots.tryAcquire()) {
      observability.recordAgentRateLimitBlocked("capacity", action);
      throw new AppException(
          "AGENT_CHAT_CAPACITY_EXCEEDED", "Chat is at capacity. Please retry shortly.", 503);
    }
    return chatTurnSlots;
  }

  private void releaseLocalSlot(Semaphore localSlot) {
    if (localSlot != null) {
      localSlot.release();
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
    private final Semaphore localSlot;
    private final AtomicBoolean closed = new AtomicBoolean();

    private Slot(
        RateLimitStore rateLimitStore,
        AiObservability observability,
        String key,
        String action,
        boolean acquired,
        Semaphore localSlot) {
      this.rateLimitStore = rateLimitStore;
      this.observability = observability;
      this.key = key;
      this.action = action;
      this.acquired = acquired;
      this.localSlot = localSlot;
    }

    private static Slot noop() {
      return new Slot(null, null, null, null, false, null);
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) {
        return;
      }
      try {
        if (acquired) {
          rateLimitStore.release(key);
        }
      } catch (RuntimeException exception) {
        observability.recordAgentRateLimitBackendFailure("concurrency-release:" + action);
      } finally {
        if (localSlot != null) {
          localSlot.release();
        }
      }
    }
  }
}
