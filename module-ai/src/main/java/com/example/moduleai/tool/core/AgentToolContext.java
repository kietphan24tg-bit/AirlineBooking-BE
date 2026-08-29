package com.example.moduleai.tool.core;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.ai.chat.model.ToolContext;

public record AgentToolContext(
    UUID conversationId,
    UUID proposalId,
    Long userId,
    Set<String> roles,
    boolean confirmed,
    String confirmationToken,
    String idempotencyKey,
    Long verifiedBookingId,
    Map<String, Object> rawContext) {

  public static final String CONVERSATION_ID_KEY = "conversationId";
  public static final String PROPOSAL_ID_KEY = "proposalId";
  public static final String USER_ID_KEY = "userId";
  public static final String ROLES_KEY = "roles";
  public static final String CONFIRMED_KEY = "confirmed";
  public static final String CONFIRMATION_TOKEN_KEY = "confirmationToken";
  public static final String IDEMPOTENCY_KEY = "idempotencyKey";
  public static final String VERIFIED_BOOKING_ID_KEY = "verifiedBookingId";

  public static AgentToolContext empty() {
    return new AgentToolContext(null, null, null, Set.of(), false, null, null, null, Map.of());
  }

  public static AgentToolContext from(ToolContext toolContext) {
    if (toolContext == null || toolContext.getContext() == null) {
      return empty();
    }
    Map<String, Object> raw = Map.copyOf(toolContext.getContext());
    return new AgentToolContext(
        uuidValue(raw.get(CONVERSATION_ID_KEY)),
        uuidValue(raw.get(PROPOSAL_ID_KEY)),
        longValue(raw.get(USER_ID_KEY)),
        rolesOf(raw.get(ROLES_KEY)),
        booleanValue(raw.get(CONFIRMED_KEY)),
        stringValue(raw.get(CONFIRMATION_TOKEN_KEY)),
        stringValue(raw.get(IDEMPOTENCY_KEY)),
        longValue(raw.get(VERIFIED_BOOKING_ID_KEY)),
        raw);
  }

  public Map<String, Object> toToolContextMap() {
    return Map.copyOf(rawContext);
  }

  public AgentToolContext withVerifiedBookingId(Long bookingId) {
    java.util.LinkedHashMap<String, Object> trustedContext =
        new java.util.LinkedHashMap<>(rawContext);
    trustedContext.put(VERIFIED_BOOKING_ID_KEY, bookingId);
    return new AgentToolContext(
        conversationId,
        proposalId,
        userId,
        roles,
        confirmed,
        confirmationToken,
        idempotencyKey,
        bookingId,
        Map.copyOf(trustedContext));
  }

  public AgentToolContext withConversationId(UUID value) {
    java.util.LinkedHashMap<String, Object> trustedContext =
        new java.util.LinkedHashMap<>(rawContext);
    if (value != null) {
      trustedContext.put(CONVERSATION_ID_KEY, value.toString());
    } else {
      trustedContext.remove(CONVERSATION_ID_KEY);
    }
    return new AgentToolContext(
        value,
        proposalId,
        userId,
        roles,
        confirmed,
        confirmationToken,
        idempotencyKey,
        verifiedBookingId,
        Map.copyOf(trustedContext));
  }

  private static Set<String> rolesOf(Object value) {
    if (value instanceof Collection<?> collection) {
      return collection.stream()
          .filter(Objects::nonNull)
          .map(Object::toString)
          .map(String::trim)
          .filter(role -> !role.isEmpty())
          .collect(Collectors.toUnmodifiableSet());
    }
    if (value instanceof String stringValue && !stringValue.isBlank()) {
      return Set.of(stringValue.trim());
    }
    return Set.of();
  }

  private static Long longValue(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String stringValue && !stringValue.isBlank()) {
      try {
        return Long.valueOf(stringValue.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static boolean booleanValue(Object value) {
    if (value instanceof Boolean booleanValue) {
      return booleanValue;
    }
    if (value instanceof String stringValue && !stringValue.isBlank()) {
      return Boolean.parseBoolean(stringValue.trim());
    }
    return false;
  }

  private static String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    String stringValue = value.toString().trim();
    return stringValue.isEmpty() ? null : stringValue;
  }

  private static UUID uuidValue(Object value) {
    String stringValue = stringValue(value);
    if (stringValue == null) {
      return null;
    }
    try {
      return UUID.fromString(stringValue);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
