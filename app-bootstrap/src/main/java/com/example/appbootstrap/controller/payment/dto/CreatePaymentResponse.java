package com.example.appbootstrap.controller.payment.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreatePaymentResponse(PaymentResponse payment, PaymentActionResponse action) {

  public record PaymentResponse(
      Long id,
      Long bookingId,
      BigDecimal amount,
      String currency,
      String method,
      String status,
      OffsetDateTime expireAt,
      String transactionId,
      String qrData) {}

  public record PaymentActionResponse(
      String type, String qrData, String qrImageUrl, String paymentUrl) {}
}
