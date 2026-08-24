package com.example.modulepayment.gateway;

import com.example.common.exception.AppException;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.gateway.PaymentGateway.PaymentLineItem;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentGateway {

  private static final String ACTION_TYPE_CHECKOUT = "CHECKOUT";

  /** Stripe Checkout allows {@code expires_at} between 30 minutes and 24 hours after creation. */
  static final long STRIPE_MIN_CHECKOUT_TTL_SECONDS = 30L * 60L;

  static final long STRIPE_MAX_CHECKOUT_TTL_SECONDS = 24L * 60L * 60L;

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
            .setPaymentIntentData(paymentIntentData.build());

    addMetadata(params, request.metadata());
    addLineItems(params, request);

    Long expiresAt = checkoutExpiresAt(request.expiredAt());
    if (expiresAt != null) {
      params.setExpiresAt(expiresAt);
    }

    try {
      Session session = Session.create(params.build(), requestOptions(request.idempotencyKey()));
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

  private static void addLineItems(
      SessionCreateParams.Builder params, PaymentGateway.PaymentGatewayRequest request) {
    List<PaymentLineItem> lineItems = request.lineItems();
    if (lineItems == null || lineItems.isEmpty()) {
      params.addLineItem(
          buildLineItem(
              request.productName(), request.productDescription(), request.amount(), request));
      return;
    }
    boolean addedLineItem = false;
    for (PaymentLineItem lineItem : lineItems) {
      if (lineItem == null
          || lineItem.amount() == null
          || BigDecimal.ZERO.compareTo(lineItem.amount()) == 0) {
        continue;
      }
      params.addLineItem(
          buildLineItem(lineItem.name(), lineItem.description(), lineItem.amount(), request));
      addedLineItem = true;
    }
    if (!addedLineItem) {
      params.addLineItem(
          buildLineItem(
              request.productName(), request.productDescription(), request.amount(), request));
    }
  }

  private static SessionCreateParams.LineItem buildLineItem(
      String name,
      String description,
      BigDecimal amount,
      PaymentGateway.PaymentGatewayRequest request) {
    SessionCreateParams.LineItem.PriceData.ProductData.Builder productData =
        SessionCreateParams.LineItem.PriceData.ProductData.builder()
            .setName(name)
            .setDescription(description)
            .putMetadata("paymentId", String.valueOf(request.paymentId()))
            .putMetadata("bookingId", String.valueOf(request.bookingId()));
    addMetadata(productData, request.metadata());

    return SessionCreateParams.LineItem.builder()
        .setQuantity(1L)
        .setPriceData(
            SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency(request.currency().toLowerCase())
                .setUnitAmount(toStripeAmount(amount, request.currency()))
                .setProductData(productData.build())
                .build())
        .build();
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

  public String resolvePaymentIntent(String sessionId) {
    if (!paymentProperties.isStripeConfigured()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "Stripe provider is not fully configured", 500);
    }
    if (sessionId == null || sessionId.isBlank()) {
      throw new AppException(
          "REFUND_PAYMENT_REFERENCE_MISSING", "Stripe session id is required", 409);
    }
    try {
      Session session = Session.retrieve(sessionId, requestOptions());
      String paymentIntentId = session.getPaymentIntent();
      if (paymentIntentId == null || paymentIntentId.isBlank()) {
        throw new AppException(
            "REFUND_PAYMENT_REFERENCE_MISSING",
            "Stripe payment intent is not available for refund",
            409);
      }
      return paymentIntentId;
    } catch (StripeException exception) {
      throw new AppException(
          "PAYMENT_PROVIDER_ERROR", "Stripe rejected payment reference lookup", 502);
    }
  }

  public CheckoutSessionStatus getCheckoutSessionStatus(String sessionId) {
    if (!paymentProperties.isStripeConfigured()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "Stripe provider is not fully configured", 500);
    }
    if (sessionId == null || sessionId.isBlank()) {
      throw new AppException("PAYMENT_REFERENCE_MISSING", "Stripe session id is required", 409);
    }
    try {
      Session session = Session.retrieve(sessionId, requestOptions());
      return new CheckoutSessionStatus(
          session.getId(),
          session.getPaymentIntent(),
          session.getStatus(),
          session.getPaymentStatus(),
          session.getAmountTotal(),
          session.getCurrency());
    } catch (StripeException exception) {
      throw new AppException("PAYMENT_PROVIDER_ERROR", "Stripe rejected status lookup", 502);
    }
  }

  public RefundResult createRefund(
      String paymentIntentId, BigDecimal amount, String currency, String reason) {
    if (!paymentProperties.isStripeConfigured()) {
      throw new AppException(
          "PAYMENT_PROVIDER_MISCONFIGURED", "Stripe provider is not fully configured", 500);
    }
    if (paymentIntentId == null || paymentIntentId.isBlank()) {
      throw new AppException(
          "REFUND_PAYMENT_REFERENCE_MISSING", "Stripe payment intent is required", 409);
    }
    try {
      RefundCreateParams.Builder params =
          RefundCreateParams.builder()
              .setPaymentIntent(paymentIntentId)
              .setAmount(toStripeAmount(amount, currency))
              .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
      if (reason != null && !reason.isBlank()) {
        params.putMetadata("reason", reason.trim());
      }
      Refund refund = Refund.create(params.build(), requestOptions());
      return new RefundResult(refund.getId(), refund.getStatus());
    } catch (StripeException exception) {
      throw new AppException("PAYMENT_PROVIDER_ERROR", "Stripe rejected refund request", 502);
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
    return requestOptions(null);
  }

  private RequestOptions requestOptions(String idempotencyKey) {
    RequestOptions.RequestOptionsBuilder builder =
        RequestOptions.builder().setApiKey(paymentProperties.stripe().secretKey());
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      builder.setIdempotencyKey(idempotencyKey.trim());
    }
    return builder.build();
  }

  /**
   * Aligns Stripe Checkout expiry with the booking/payment cap. When the cap is sooner than
   * Stripe's 30-minute minimum, the returned value uses that minimum and callers must expire the
   * session at the cap via {@link #expireCheckoutSession(String)}.
   */
  static Long checkoutExpiresAt(OffsetDateTime expiredAt) {
    if (expiredAt == null) {
      return null;
    }
    long nowEpoch = OffsetDateTime.now().toEpochSecond();
    long capEpoch = expiredAt.toEpochSecond();
    if (capEpoch <= nowEpoch) {
      return null;
    }
    long stripeMinEpoch = nowEpoch + STRIPE_MIN_CHECKOUT_TTL_SECONDS;
    long stripeMaxEpoch = nowEpoch + STRIPE_MAX_CHECKOUT_TTL_SECONDS;
    long stripeEpoch = Math.min(Math.max(capEpoch, stripeMinEpoch), stripeMaxEpoch);
    return stripeEpoch;
  }

  private static boolean isZeroDecimal(String currency) {
    return currency != null && "VND".equalsIgnoreCase(currency.trim());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record CheckoutSessionStatus(
      String sessionId,
      String paymentIntentId,
      String status,
      String paymentStatus,
      Long amountTotal,
      String currency) {}

  public record RefundResult(String refundId, String status) {}
}
