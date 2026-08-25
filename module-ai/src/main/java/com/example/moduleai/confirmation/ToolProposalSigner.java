package com.example.moduleai.confirmation;

import com.example.moduleai.config.AiAgentProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class ToolProposalSigner {

  private final AiAgentProperties properties;

  public ToolProposalSigner(AiAgentProperties properties) {
    this.properties = properties;
  }

  public String sign(
      UUID proposalId,
      Long userId,
      String toolName,
      String operation,
      String canonicalArguments,
      OffsetDateTime expiresAt) {
    return hmac(
        "v1|"
            + proposalId
            + "|"
            + userId
            + "|"
            + toolName
            + "|"
            + operation
            + "|"
            + canonicalArguments
            + "|"
            + normalizeTimestamp(expiresAt).toInstant());
  }

  public boolean matches(
      String expected,
      UUID proposalId,
      Long userId,
      String toolName,
      String operation,
      String canonicalArguments,
      OffsetDateTime expiresAt) {
    String actual = sign(proposalId, userId, toolName, operation, canonicalArguments, expiresAt);
    return MessageDigest.isEqual(
        actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
  }

  OffsetDateTime normalizeTimestamp(OffsetDateTime timestamp) {
    Instant roundedToMicros = timestamp.toInstant().plusNanos(500).truncatedTo(ChronoUnit.MICROS);
    return OffsetDateTime.ofInstant(roundedToMicros, ZoneOffset.UTC);
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
