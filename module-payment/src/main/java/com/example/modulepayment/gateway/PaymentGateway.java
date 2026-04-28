package com.example.modulepayment.gateway;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface PaymentGateway {

  PaymentGatewayResult createPaymentLink(PaymentGatewayRequest request);

  void cancelPaymentLink(CancelPaymentLinkRequest request);

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
      OffsetDateTime expiredAt) {}

  record PaymentGatewayResult(
      String paymentLinkId,
      String checkoutUrl,
      String qrCode,
      String status,
      String actionType,
      OffsetDateTime expiredAt) {}

  record CancelPaymentLinkRequest(Long paymentId, long orderCode, String cancellationReason) {}
}
