package com.example.modulepayment.gateway;

import com.example.common.exception.AppException;
import com.example.modulepayment.config.PaymentProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PayosPaymentGateway implements PaymentGateway {

  private static final String DEFAULT_BASE_URL = "https://api-merchant.payos.vn";
  private static final String ACTION_TYPE_QR = "QR";
  private static final String HMAC_SHA_256 = "HmacSHA256";
  private static final String PAYOS_SUCCESS_CODE = "00";

  private final PaymentProperties paymentProperties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  @Autowired
  public PayosPaymentGateway(PaymentProperties paymentProperties, ObjectMapper objectMapper) {
    this(paymentProperties, objectMapper, HttpClient.newHttpClient());
  }

  PayosPaymentGateway(
      PaymentProperties paymentProperties, ObjectMapper objectMapper, HttpClient httpClient) {
    this.paymentProperties = paymentProperties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @Override
  public PaymentGatewayResult createPaymentLink(PaymentGatewayRequest request) {
    if (!paymentProperties.isPayosConfigured()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "PayOS provider is not fully configured", 500);
    }

    long amount = toPayosAmount(request.amount());
    String signaturePayload =
        buildSignaturePayload(
            amount,
            request.orderCode(),
            request.description(),
            request.returnUrl(),
            request.cancelUrl());
    String signature = sign(signaturePayload, paymentProperties.payos().checksumKey());

    CreatePaymentLinkPayload payload =
        new CreatePaymentLinkPayload(
            request.orderCode(),
            amount,
            request.description(),
            request.buyerName(),
            request.buyerEmail(),
            request.buyerPhone(),
            request.cancelUrl(),
            request.returnUrl(),
            request.expiredAt() == null ? null : request.expiredAt().toEpochSecond(),
            signature);

    URI uri =
        UriComponentsBuilder.fromUriString(resolveBaseUrl(paymentProperties))
            .path("/v2/payment-requests")
            .build()
            .toUri();

    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder(uri)
              .header("Content-Type", "application/json")
              .header("x-client-id", paymentProperties.payos().clientId())
              .header("x-api-key", paymentProperties.payos().apiKey())
              .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
              .build();

      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new AppException("PAYMENT_PROVIDER_ERROR", "PayOS rejected payment request", 502);
      }

      PayosCreatePaymentResponse paymentResponse =
          objectMapper.readValue(response.body(), PayosCreatePaymentResponse.class);
      if (paymentResponse.code() == null || !PAYOS_SUCCESS_CODE.equals(paymentResponse.code())) {
        throw new AppException(
            "PAYMENT_PROVIDER_ERROR", "PayOS returned a non-success response", 502);
      }
      if (paymentResponse.data() == null || paymentResponse.data().paymentLinkId() == null) {
        throw new AppException(
            "PAYMENT_PROVIDER_ERROR", "PayOS returned an invalid payment response", 502);
      }

      return new PaymentGatewayResult(
          paymentResponse.data().paymentLinkId(),
          paymentResponse.data().checkoutUrl(),
          paymentResponse.data().qrCode(),
          paymentResponse.data().status(),
          ACTION_TYPE_QR,
          paymentResponse.data().createdAt() == null ? request.expiredAt() : request.expiredAt());
    } catch (IOException exception) {
      throw new AppException("PAYMENT_PROVIDER_ERROR", "Unable to parse PayOS response", 502);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AppException("PAYMENT_PROVIDER_ERROR", "PayOS request was interrupted", 502);
    }
  }

  @Override
  public void cancelPaymentLink(CancelPaymentLinkRequest request) {
    if (!paymentProperties.isPayosConfigured()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "PayOS provider is not fully configured", 500);
    }
    if (request == null) {
      throw new AppException("INVALID_CANCEL_REQUEST", "Cancel request is required", 400);
    }

    URI uri =
        UriComponentsBuilder.fromUriString(resolveBaseUrl(paymentProperties))
            .path("/v2/payment-requests/")
            .path(String.valueOf(request.orderCode()))
            .path("/cancel")
            .build()
            .toUri();

    CancelPaymentLinkPayload payload = new CancelPaymentLinkPayload(request.cancellationReason());

    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder(uri)
              .header("Content-Type", "application/json")
              .header("x-client-id", paymentProperties.payos().clientId())
              .header("x-api-key", paymentProperties.payos().apiKey())
              .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
              .build();

      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new AppException("PAYMENT_PROVIDER_ERROR", "PayOS rejected cancel request", 502);
      }

      PayosCancelPaymentResponse cancelResponse =
          objectMapper.readValue(response.body(), PayosCancelPaymentResponse.class);
      if (cancelResponse.code() == null || !PAYOS_SUCCESS_CODE.equals(cancelResponse.code())) {
        throw new AppException(
            "PAYMENT_PROVIDER_ERROR", "PayOS returned a non-success cancel response", 502);
      }
    } catch (IOException exception) {
      throw new AppException("PAYMENT_PROVIDER_ERROR", "Unable to parse PayOS response", 502);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AppException("PAYMENT_PROVIDER_ERROR", "PayOS request was interrupted", 502);
    }
  }

  static String buildSignaturePayload(
      long amount, long orderCode, String description, String returnUrl, String cancelUrl) {
    return "amount="
        + amount
        + "&cancelUrl="
        + cancelUrl
        + "&description="
        + description
        + "&orderCode="
        + orderCode
        + "&returnUrl="
        + returnUrl;
  }

  static String sign(String data, String checksumKey) {
    try {
      // Tạo HMAC-SHA256 signature từ payload và checksum key theo hướng dẫn của PayOS
      Mac mac = Mac.getInstance(HMAC_SHA_256);
      mac.init(new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
      return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "Unable to generate PayOS signature", 500);
    }
  }

  static long toPayosAmount(BigDecimal amount) {
    try {
      // ép tiền về số nguyên VND
      return amount.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    } catch (ArithmeticException exception) {
      throw new AppException(
          "INVALID_PAYMENT_AMOUNT", "Payment amount must be a whole-number VND amount", 400);
    }
  }

  private static String resolveBaseUrl(PaymentProperties paymentProperties) {
    String configured =
        paymentProperties.payos() == null ? null : paymentProperties.payos().baseUrl();
    return configured == null || configured.isBlank() ? DEFAULT_BASE_URL : configured;
  }

  record CreatePaymentLinkPayload(
      long orderCode,
      long amount,
      String description,
      String buyerName,
      String buyerEmail,
      String buyerPhone,
      String cancelUrl,
      String returnUrl,
      Long expiredAt,
      String signature) {}

  record CancelPaymentLinkPayload(String cancellationReason) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record PayosCreatePaymentResponse(String code, String desc, PayosCreatePaymentData data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record PayosCreatePaymentData(
      String currency,
      @JsonProperty("paymentLinkId") String paymentLinkId,
      String status,
      @JsonProperty("checkoutUrl") String checkoutUrl,
      @JsonProperty("qrCode") String qrCode,
      @JsonProperty("createdAt") OffsetDateTime createdAt) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record PayosCancelPaymentResponse(String code, String desc, Object data) {}
}
