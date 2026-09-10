package com.example.moduleai.confirmation;

import com.example.moduleai.config.AiAgentProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class ToolProposalSigner {

  private final AiAgentProperties properties;
  private final ObjectMapper objectMapper;

  public ToolProposalSigner(AiAgentProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public String sign(
      UUID conversationId,
      UUID proposalId,
      Long userId,
      String toolName,
      String operation,
      String canonicalArguments,
      OffsetDateTime expiresAt) {
    return hmac(
        "v2|"
            + conversationId
            + "|"
            + proposalId
            + "|"
            + userId
            + "|"
            + toolName
            + "|"
            + operation
            + "|"
            + canonicalizeArguments(canonicalArguments)
            + "|"
            + normalizeTimestamp(expiresAt).toInstant());
  }

  public boolean matches(
      String expected,
      UUID conversationId,
      UUID proposalId,
      Long userId,
      String toolName,
      String operation,
      String canonicalArguments,
      OffsetDateTime expiresAt) {
    String actual =
        sign(
            conversationId, proposalId, userId, toolName, operation, canonicalArguments, expiresAt);
    return MessageDigest.isEqual(
        actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
  }

  OffsetDateTime normalizeTimestamp(OffsetDateTime timestamp) {
    Instant roundedToMicros = timestamp.toInstant().plusNanos(500).truncatedTo(ChronoUnit.MICROS);
    return OffsetDateTime.ofInstant(roundedToMicros, ZoneOffset.UTC);
  }

  private String canonicalizeArguments(String arguments) {
    try {
      return canonicalize(objectMapper.readTree(arguments)).toString();
    } catch (JsonProcessingException exception) {
      // The typed tool validation reports malformed input later. Preserve the exact bytes for HMAC
      // generation so the signer remains deterministic for rejected proposals too.
      return arguments;
    }
  }

  private JsonNode canonicalize(JsonNode node) {
    if (node.isObject()) {
      ObjectNode canonical = objectMapper.createObjectNode();
      node.properties().stream()
          .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
          .forEach(entry -> canonical.set(entry.getKey(), canonicalize(entry.getValue())));
      return canonical;
    }
    if (node.isArray()) {
      ArrayNode canonical = objectMapper.createArrayNode();
      node.forEach(element -> canonical.add(canonicalize(element)));
      return canonical;
    }
    return node;
  }

  private String hmac(String payload) {
    String secret = properties.getConfirmationHmacSecret();
    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException(
          "APP_AI_AGENT_CONFIRMATION_HMAC_SECRET must be configured with at least 32 characters");
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to compute proposal HMAC", exception);
    }
  }
}
