package com.example.modulepayment.gateway;

import com.example.common.exception.AppException;
import com.example.modulepayment.config.PaymentProperties;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentGateway {

  private static final String ACTION_TYPE_CHECKOUT = "CHECKOUT";
  private static final long MIN_CHECKOUT_TTL_SECONDS = 30L * 60L;

  private final PaymentProperties paymentProperties;

  public StripePaymentGateway(PaymentProperties paymentProperties) {
    this.paymentProperties = paymentProperties;
  }

  public PaymentGateway.PaymentGatewayResult createCheckoutSession(
      PaymentGateway.PaymentGatewayRequest request) {
    if (!paymentProperties.isStripeConfigured()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "Stripe provider is not fully configured", 500);
    }

    SessionCreateParams.PaymentIntentData.Builder paymentIntentData =
        SessionCreateParams.PaymentIntentData.builder()
            .putMetadata("provider", "STRIPE")
            .putMetadata("paymentId", String.valueOf(request.paymentId()))
            .putMetadata("bookingId", String.valueOf(request.bookingId()));
    addMetadata(paymentIntentData, request.metadata());

    SessionCreateParams.LineItem.PriceData.ProductData.Builder productData =
        SessionCreateParams.LineItem.PriceData.ProductData.builder()
            .setName(request.productName())
            .setDescription(request.productDescription())
            .putMetadata("paymentId", String.valueOf(request.paymentId()))
            .putMetadata("bookingId", String.valueOf(request.bookingId()));
    addMetadata(productData, request.metadata());

    SessionCreateParams.Builder params =
        SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(request.returnUrl())
            .setCancelUrl(request.cancelUrl())
            .setCustomerEmail(blankToNull(request.buyerEmail()))
            .putMetadata("provider", "STRIPE")
            .putMetadata("paymentId", String.valueOf(request.paymentId()))
            .putMetadata("bookingId", String.valueOf(request.bookingId()))
            .putMetadata("orderCode", String.valueOf(request.orderCode()))
            .setPaymentIntentData(paymentIntentData.build())
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(request.currency().toLowerCase())
                            .setUnitAmount(toStripeAmount(request.amount(), request.currency()))
                            .setProductData(productData.build())
                            .build())
                    .build());

    addMetadata(params, request.metadata());

    Long expiresAt = checkoutExpiresAt(request.expiredAt());
    if (expiresAt != null) {
      params.setExpiresAt(expiresAt);
    }

    try {
      Session session = Session.create(params.build(), requestOptions());
      return new PaymentGateway.PaymentGatewayResult(
          session.getId(),
          session.getUrl(),
          null,
          session.getStatus(),
          ACTION_TYPE_CHECKOUT,
          request.expiredAt());
    } catch (StripeException exception) {
      throw new AppException("PAYMENT_PROVIDER_ERROR", "Stripe rejected payment request", 502);
    }
  }

  private static void addMetadata(
      SessionCreateParams.Builder params, Map<String, String> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return;
    }
    for (Map.Entry<String, String> entry : metadata.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        params.putMetadata(entry.getKey(), entry.getValue());
      }
    }
  }

  private static void addMetadata(
      SessionCreateParams.PaymentIntentData.Builder params, Map<String, String> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return;
    }
    for (Map.Entry<String, String> entry : metadata.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        params.putMetadata(entry.getKey(), entry.getValue());
      }
    }
  }

  private static void addMetadata(
      SessionCreateParams.LineItem.PriceData.ProductData.Builder params,
      Map<String, String> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return;
    }
    for (Map.Entry<String, String> entry : metadata.entrySet()) {
      if (entry.getKey() != null && entry.getValue() != null) {
        params.putMetadata(entry.getKey(), entry.getValue());
      }
    }
  }

  public void expireCheckoutSession(String sessionId) {
    if (!paymentProperties.isStripeConfigured()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "Stripe provider is not fully configured", 500);
    }
    if (sessionId == null || sessionId.isBlank()) {
      return;
    }

    try {
      Session.retrieve(sessionId, requestOptions()).expire(requestOptions());
    } catch (StripeException exception) {
      throw new AppException("PAYMENT_PROVIDER_ERROR", "Stripe rejected cancel request", 502);
    }
  }

  static long toStripeAmount(BigDecimal amount, String currency) {
    if (amount == null) {
      throw new AppException("INVALID_PAYMENT_AMOUNT", "Payment amount is required", 400);
    }
    try {
      BigDecimal normalized =
          isZeroDecimal(currency)
              ? amount.setScale(0, RoundingMode.UNNECESSARY)
              : amount.movePointRight(2);
      return normalized.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    } catch (ArithmeticException exception) {
      throw new AppException(
          "INVALID_PAYMENT_AMOUNT", "Payment amount is invalid for Stripe currency units", 400);
    }
  }

  private RequestOptions requestOptions() {
    return RequestOptions.builder().setApiKey(paymentProperties.stripe().secretKey()).build();
  }

  private static Long checkoutExpiresAt(OffsetDateTime expiredAt) {
    if (expiredAt == null) {
      return null;
    }
    long expiresAt = expiredAt.toEpochSecond();
    long minExpiresAt = OffsetDateTime.now().toEpochSecond() + MIN_CHECKOUT_TTL_SECONDS;
    return expiresAt >= minExpiresAt ? expiresAt : null;
  }

  private static boolean isZeroDecimal(String currency) {
    return currency != null && "VND".equalsIgnoreCase(currency.trim());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
