package com.example.modulepayment.gateway;

import com.example.common.exception.AppException;
import com.example.modulepayment.config.PaymentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import org.springframework.stereotype.Component;

@Component
public class PayosWebhookSupport {

  private static final String EVENT_TYPE_FAILED = "PAYMENT_FAILED";
  private static final String EVENT_TYPE_SUCCEEDED = "PAYMENT_SUCCEEDED";
  private static final String PROVIDER_PAYOS = "PAYOS";
  private static final ZoneId PAYOS_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final DateTimeFormatter TRANSACTION_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ObjectMapper objectMapper;
  private final PaymentProperties paymentProperties;

  public PayosWebhookSupport(ObjectMapper objectMapper, PaymentProperties paymentProperties) {
    this.objectMapper = objectMapper;
    this.paymentProperties = paymentProperties;
  }

  public NormalizedPayosWebhookEvent parseAndVerify(String rawPayload) {
    try {
      JsonNode root = objectMapper.readTree(rawPayload);
      if (root == null || !root.isObject()) {
        throw badRequest("Webhook payload must be a JSON object");
      }

      JsonNode data = requireObject(root, "data");
      String signature = requireText(root, "signature");
      String expectedSignature =
          PayosPaymentGateway.sign(
              buildWebhookSignaturePayload(data), paymentProperties.payos().checksumKey());

      if (!MessageDigest.isEqual(
          expectedSignature.getBytes(StandardCharsets.UTF_8),
          signature.getBytes(StandardCharsets.UTF_8))) {
        throw new AppException(
            "PAYMENT_WEBHOOK_INVALID_SIGNATURE", "PayOS webhook signature is invalid", 401);
      }

      PayosWebhookPayload payload = objectMapper.treeToValue(root, PayosWebhookPayload.class);
      PayosWebhookData webhookData = payload.data();
      if (webhookData == null) {
        throw badRequest("Webhook payload is missing data");
      }

      String eventType = isSuccessPayload(payload) ? EVENT_TYPE_SUCCEEDED : EVENT_TYPE_FAILED;
      OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
      OffsetDateTime occurredAt = parseOccurredAt(webhookData.transactionDateTime(), receivedAt);

      return new NormalizedPayosWebhookEvent(
          PROVIDER_PAYOS,
          eventType,
          webhookData.orderCode(),
          requireNonBlank(webhookData.paymentLinkId(), "data.paymentLinkId"),
          webhookData.amount(),
          requireNonBlank(webhookData.currency(), "data.currency"),
          occurredAt,
          signature,
          rawPayload);
    } catch (AppException exception) {
      throw exception;
    } catch (IOException exception) {
      throw badRequest("Webhook payload could not be parsed");
    }
  }

  // Normalize the PayOS webhook payload into a standard format that our application can use, and
  // verify the signature to ensure the webhook is authentic and has not been tampered with
  static String buildWebhookSignaturePayload(JsonNode dataNode) {
    List<String> fieldNames = new ArrayList<>();
    Iterator<Entry<String, JsonNode>> fields = dataNode.fields();
    while (fields.hasNext()) {
      fieldNames.add(fields.next().getKey());
    }
    Collections.sort(fieldNames);

    List<String> parts = new ArrayList<>(fieldNames.size());
    for (String fieldName : fieldNames) {
      JsonNode valueNode = dataNode.get(fieldName);
      parts.add(fieldName + "=" + scalarValue(valueNode));
    }
    return String.join("&", parts);
  }

  // convert a JsonNode value to a string for signature generation, following PayOS's rules for how
  // to serialize different types of values
  private static String scalarValue(JsonNode valueNode) {
    if (valueNode == null || valueNode.isNull()) {
      return "";
    }
    if (valueNode.isTextual()) {
      return valueNode.textValue();
    }
    if (valueNode.isNumber() || valueNode.isBoolean()) {
      return valueNode.asText();
    }
    return valueNode.toString();
  }

  private static JsonNode requireObject(JsonNode parent, String fieldName) {
    JsonNode node = parent.get(fieldName);
    if (node == null || node.isNull() || !node.isObject()) {
      throw badRequest("Webhook payload is missing " + fieldName);
    }
    return node;
  }

  private static String requireText(JsonNode parent, String fieldName) {
    JsonNode node = parent.get(fieldName);
    if (node == null || node.isNull() || node.asText().isBlank()) {
      throw badRequest("Webhook payload is missing " + fieldName);
    }
    return node.asText();
  }

  private static String requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw badRequest("Webhook payload is missing " + fieldName);
    }
    return value;
  }

  private static OffsetDateTime parseOccurredAt(String rawValue, OffsetDateTime fallback) {
    if (rawValue == null || rawValue.isBlank()) {
      return fallback; // return now if transactionDateTime is missing or blank, to avoid rejecting
      // the webhook just because of a timestamp issue
    }
    try {
      LocalDateTime localDateTime = LocalDateTime.parse(rawValue, TRANSACTION_TIME_FORMATTER);
      return localDateTime.atZone(PAYOS_ZONE_ID).toOffsetDateTime();
      // normalize the transactionDateTime to UTC+0, so that we can compare/sort it reliably
      // regardless of the timezone of the PayOS account
    } catch (DateTimeParseException exception) {
      return fallback;
    }
  }

  // check if the payload indicates a successful payment according to PayOS's rules
  private static boolean isSuccessPayload(PayosWebhookPayload payload) {
    return Boolean.TRUE.equals(payload.success())
        && "00".equals(payload.code())
        && payload.data() != null
        && "00".equals(payload.data().code());
  }

  private static AppException badRequest(String message) {
    return new AppException("PAYMENT_WEBHOOK_BAD_REQUEST", message, 400);
  }

  record PayosWebhookPayload(
      String code, String desc, Boolean success, PayosWebhookData data, String signature) {}

  record PayosWebhookData(
      String accountNumber,
      Long amount,
      String description,
      String reference,
      String transactionDateTime,
      String virtualAccountNumber,
      String counterAccountBankId,
      String counterAccountBankName,
      String counterAccountName,
      String counterAccountNumber,
      String virtualAccountName,
      String currency,
      Long orderCode,
      String paymentLinkId,
      String code,
      String desc) {}

  public record NormalizedPayosWebhookEvent(
      String provider,
      String eventType,
      Long orderCode,
      String paymentLinkId,
      Long amount,
      String currency,
      OffsetDateTime occurredAt,
      String signature,
      String rawPayload) {

    public String idempotencyKey() {
      return provider + "_" + orderCode + "_" + paymentLinkId + "_" + eventType;
    }
  }
}
