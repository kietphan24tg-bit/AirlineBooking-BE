package com.example.moduleai.guardrail;

import com.example.common.exception.AppException;
import com.example.moduleai.config.AiAgentProperties;
import com.example.moduleai.observability.AiObservability;
import com.example.moduleidentity.port.RateLimitStore;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Applies distributed request limits without exposing identifiers in metrics. */
@Component
public class AgentRequestLimiter {

  private final RateLimitStore rateLimitStore;
  private final AiAgentProperties properties;
  private final AiObservability observability;

  public AgentRequestLimiter(
      RateLimitStore rateLimitStore, AiAgentProperties properties, AiObservability observability) {
    this.rateLimitStore = rateLimitStore;
    this.properties = properties;
    this.observability = observability;
  }

  public void checkQuery(Long userId, String clientIp) {
    consume(
        "query",
        "ip",
        normalizeClientIp(clientIp),
        properties.getQueryRateLimitWindow(),
        properties.getQueryRateLimitPerIp(),
        properties.isQueryRateLimitFailOpen());
    if (userId != null) {
      consume(
          "query",
          "user",
          String.valueOf(userId),
          properties.getQueryRateLimitWindow(),
          properties.getQueryRateLimitPerUser(),
          properties.isQueryRateLimitFailOpen());
    }
  }

  public void checkConfirmation(Long userId) {
    if (userId == null) {
      throw new AppException("AUTHENTICATION_REQUIRED", "Authentication is required", 401);
    }
    consume(
        "confirm",
        "user",
        String.valueOf(userId),
        properties.getConfirmationRateLimitWindow(),
        properties.getConfirmationRateLimitPerUser(),
        false);
  }

  private void consume(
      String action,
      String dimension,
      String identifier,
      Duration window,
      int limit,
      boolean failOpen) {
    if (limit <= 0) {
      return;
    }
    try {
      long count = rateLimitStore.increment(key(action, dimension, identifier), window);
      if (count > limit) {
        observability.recordAgentRateLimitBlocked(action, dimension);
        throw new AppException(
            "AGENT_RATE_LIMIT_EXCEEDED", "Too many Agent requests. Please try again later.", 429);
      }
    } catch (AppException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      observability.recordAgentRateLimitBackendFailure(action);
      if (!failOpen) {
        throw new AppException(
            "AGENT_RATE_LIMIT_UNAVAILABLE",
            "Agent action is temporarily unavailable. Please try again later.",
            503);
      }
    }
  }

  private static String key(String action, String dimension, String identifier) {
    return "rate-limit:agent:" + action + ":" + dimension + ":" + identifier;
  }

  private static String normalizeClientIp(String clientIp) {
    return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
  }
}
