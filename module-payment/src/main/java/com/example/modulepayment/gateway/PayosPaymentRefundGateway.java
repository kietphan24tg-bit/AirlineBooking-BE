package com.example.modulepayment.gateway;

import com.example.common.exception.AppException;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.PaymentEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PayosPaymentRefundGateway implements PaymentRefundGateway {

  private static final String PAYOS_SUCCESS_CODE = "00";
  private static final String DEFAULT_BASE_URL = "https://api-merchant.payos.vn";

  private final PaymentProperties paymentProperties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  @Autowired
  public PayosPaymentRefundGateway(PaymentProperties paymentProperties, ObjectMapper objectMapper) {
    this(paymentProperties, objectMapper, HttpClient.newHttpClient());
  }

  PayosPaymentRefundGateway(
      PaymentProperties paymentProperties, ObjectMapper objectMapper, HttpClient httpClient) {
    this.paymentProperties = paymentProperties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @Override
  public String provider() {
    return "PAYOS";
  }

  @Override
  public void preparePaymentForRefund(PaymentEntity payment) {
    if (payment == null) {
      throw new AppException("PAYMENT_NOT_FOUND", "Payment not found", 404);
    }
    if (hasRefundDestination(payment)) {
      return;
    }
    CounterAccount account = resolveCounterAccount(payment);
    payment.setRefundCounterAccountBankId(account.bankId());
    payment.setRefundCounterAccountBankName(account.bankName());
    payment.setRefundCounterAccountName(account.accountName());
    payment.setRefundCounterAccountNumber(account.accountNumber());
  }

  @Override
  public PaymentRefundResult refund(PaymentRefundRequest request) {
    if (!paymentProperties.isPayosConfigured()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "PayOS provider is not fully configured", 500);
    }
    PaymentEntity payment = request.payment();
    if (!hasRefundDestination(payment)) {
      throw new AppException(
          "REFUND_DESTINATION_UNAVAILABLE",
          "PayOS refund destination account is not available",
          409);
    }

    long amount = PayosPaymentGateway.toPayosAmount(request.amount());
    String referenceId = "refund-" + request.cancellationId();
    CreatePayoutPayload payload =
        new CreatePayoutPayload(
            referenceId,
            amount,
            buildDescription(payment, request.reason()),
            payment.getRefundCounterAccountBankId(),
            payment.getRefundCounterAccountNumber());

    URI uri =
        UriComponentsBuilder.fromUriString(resolveBaseUrl()).path("/v1/payouts").build().toUri();

    try {
      String requestBody = objectMapper.writeValueAsString(payload);
      HttpRequest httpRequest =
          HttpRequest.newBuilder(uri)
              .header("Content-Type", "application/json")
              .header("x-client-id", paymentProperties.payos().clientId())
              .header("x-api-key", paymentProperties.payos().apiKey())
              .header("x-idempotency-key", request.idempotencyKey())
              .header(
                  "x-signature",
                  PayosPaymentGateway.sign(
                      buildPayoutSignaturePayload(payload),
                      paymentProperties.payos().checksumKey()))
              .POST(HttpRequest.BodyPublishers.ofString(requestBody))
              .build();

      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new AppException("PAYMENT_PROVIDER_ERROR", "PayOS rejected refund request", 502);
      }

      JsonNode root = objectMapper.readTree(response.body());
      if (!PAYOS_SUCCESS_CODE.equals(text(root.path("code")))) {
        throw new AppException(
            "PAYMENT_PROVIDER_ERROR", "PayOS returned a non-success refund response", 502);
      }

      JsonNode data = root.path("data");
      return new PaymentRefundResult(
          firstNonBlank(
              text(data.path("id")),
              text(data.path("referenceId")),
              text(data.path("transactionId")),
              referenceId),
          firstNonBlank(
              text(data.path("approvalState")),
              text(data.path("state")),
              text(data.path("status")),
              "SUCCEEDED"));
    } catch (IOException exception) {
      throw new AppException("PAYMENT_PROVIDER_ERROR", "Unable to parse PayOS response", 502);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AppException("PAYMENT_PROVIDER_ERROR", "PayOS request was interrupted", 502);
    }
  }

  private CounterAccount resolveCounterAccount(PaymentEntity payment) {
    String paymentReference =
        firstNonBlank(
            payment.getTransactionId(),
            payment.getId() == null ? null : payment.getId().toString());
    if (paymentReference == null) {
      throw new AppException(
          "REFUND_DESTINATION_UNAVAILABLE", "PayOS payment reference is not available", 409);
    }
    URI uri =
        UriComponentsBuilder.fromUriString(resolveBaseUrl())
            .path("/v2/payment-requests/")
            .path(paymentReference)
            .build()
            .toUri();

    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder(uri)
              .header("x-client-id", paymentProperties.payos().clientId())
              .header("x-api-key", paymentProperties.payos().apiKey())
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new AppException(
            "REFUND_DESTINATION_UNAVAILABLE", "PayOS payment details are unavailable", 409);
      }
      JsonNode root = objectMapper.readTree(response.body());
      if (!PAYOS_SUCCESS_CODE.equals(text(root.path("code")))) {
        throw new AppException(
            "REFUND_DESTINATION_UNAVAILABLE", "PayOS payment details are unavailable", 409);
      }
      CounterAccount account = extractCounterAccount(root.path("data"));
      if (account == null || account.bankId() == null || account.accountNumber() == null) {
        throw new AppException(
            "REFUND_DESTINATION_UNAVAILABLE",
            "PayOS counter account is not available for refund",
            409);
      }
      return account;
    } catch (IOException exception) {
      throw new AppException("PAYMENT_PROVIDER_ERROR", "Unable to parse PayOS response", 502);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AppException("PAYMENT_PROVIDER_ERROR", "PayOS request was interrupted", 502);
    }
  }

  private CounterAccount extractCounterAccount(JsonNode data) {
    if (data == null || data.isMissingNode() || data.isNull()) {
      return null;
    }
    JsonNode directAccount = data.path("transactions");
    if (directAccount.isArray()) {
      for (JsonNode item : directAccount) {
        CounterAccount account =
            new CounterAccount(
                text(item.path("counterAccountBankId")),
                text(item.path("counterAccountBankName")),
                text(item.path("counterAccountName")),
                text(item.path("counterAccountNumber")));
        if (account.bankId() != null && account.accountNumber() != null) {
          return account;
        }
      }
    }
    CounterAccount fallback =
        new CounterAccount(
            text(data.path("counterAccountBankId")),
            text(data.path("counterAccountBankName")),
            text(data.path("counterAccountName")),
            text(data.path("counterAccountNumber")));
    if (fallback.bankId() != null && fallback.accountNumber() != null) {
      return fallback;
    }
    return null;
  }

  private boolean hasRefundDestination(PaymentEntity payment) {
    return payment.getRefundCounterAccountBankId() != null
        && !payment.getRefundCounterAccountBankId().isBlank()
        && payment.getRefundCounterAccountNumber() != null
        && !payment.getRefundCounterAccountNumber().isBlank();
  }

  private String buildPayoutSignaturePayload(CreatePayoutPayload payload) {
    Map<String, String> fields = new TreeMap<>();
    fields.put("amount", String.valueOf(payload.amount()));
    fields.put("description", encode(payload.description()));
    fields.put("referenceId", encode(payload.referenceId()));
    fields.put("toAccountNumber", encode(payload.toAccountNumber()));
    fields.put("toBin", encode(payload.toBin()));
    return fields.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .reduce((left, right) -> left + "&" + right)
        .orElse("");
  }

  private static String buildDescription(PaymentEntity payment, String reason) {
    String base =
        payment == null || payment.getBooking() == null
            ? "Booking refund"
            : "Refund " + payment.getBooking().getPnrCode();
    if (reason == null || reason.isBlank()) {
      return base;
    }
    String combined = base + " - " + reason.trim();
    return combined.length() <= 255 ? combined : combined.substring(0, 255);
  }

  private static String encode(String value) {
    if (value == null) {
      return "";
    }
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private String resolveBaseUrl() {
    String configured =
        paymentProperties.payos() == null ? null : paymentProperties.payos().baseUrl();
    return configured == null || configured.isBlank() ? DEFAULT_BASE_URL : configured;
  }

  private static String text(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return value == null || value.isBlank() ? null : value;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private record CreatePayoutPayload(
      String referenceId, long amount, String description, String toBin, String toAccountNumber) {}

  private record CounterAccount(
      String bankId, String bankName, String accountName, String accountNumber) {}
}
