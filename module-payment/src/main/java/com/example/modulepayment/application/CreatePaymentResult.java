package com.example.modulepayment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreatePaymentResult(PaymentView payment, PaymentActionView action) {

  public record PaymentView(
      Long id,
      Long bookingId,
      BigDecimal amount,
      String currency,
      String method,
      String status,
      OffsetDateTime expireAt,
      String transactionId,
      String qrData) {}

  public record PaymentActionView(
      String type, String qrData, String qrImageUrl, String paymentUrl) {}
}
