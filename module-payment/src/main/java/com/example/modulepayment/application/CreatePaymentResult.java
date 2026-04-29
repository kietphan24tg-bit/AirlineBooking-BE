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
      String qrData,
      PaymentDetailsView details) {

    public PaymentView(
        Long id,
        Long bookingId,
        BigDecimal amount,
        String currency,
        String method,
        String status,
        OffsetDateTime expireAt,
        String transactionId,
        String qrData) {
      this(id, bookingId, amount, currency, method, status, expireAt, transactionId, qrData, null);
    }
  }

  public record PaymentActionView(
      String type, String qrData, String qrImageUrl, String paymentUrl) {}
}
