package com.example.modulepayment.gateway;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public interface PaymentGateway {

  PaymentGatewayResult createPaymentLink(PaymentGatewayRequest request);

  default void cancelPaymentLink(CancelPaymentLinkRequest request) {
    throw new UnsupportedOperationException("Payment provider does not support cancellation");
  }

  record PaymentGatewayRequest(
      Long paymentId,
      long orderCode,
      BigDecimal amount,
      String currency,
      String description,
      String returnUrl,
      String cancelUrl,
      String buyerName,
      String buyerEmail,
      String buyerPhone,
      OffsetDateTime expiredAt,
      Long bookingId,
      String provider,
      String productName,
      String productDescription,
      Map<String, String> metadata) {

    public PaymentGatewayRequest(
        Long paymentId,
        long orderCode,
        BigDecimal amount,
        String currency,
        String description,
        String returnUrl,
        String cancelUrl,
        String buyerName,
        String buyerEmail,
        String buyerPhone,
        OffsetDateTime expiredAt) {
      this(
          paymentId,
          orderCode,
          amount,
          currency,
          description,
          returnUrl,
          cancelUrl,
          buyerName,
          buyerEmail,
          buyerPhone,
          expiredAt,
          null,
          null,
          description,
          description,
          Map.of());
    }
  }

  record PaymentGatewayResult(
      String paymentLinkId,
      String checkoutUrl,
      String qrCode,
      String status,
      String actionType,
      OffsetDateTime expiredAt) {}

  record CancelPaymentLinkRequest(Long paymentId, long orderCode, String cancellationReason) {}
}
