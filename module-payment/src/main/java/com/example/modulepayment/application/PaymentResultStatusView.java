package com.example.modulepayment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentResultStatusView(
    Long paymentId,
    Long bookingId,
    String paymentStatus,
    String bookingStatus,
    String resultPage,
    OffsetDateTime expireAt,
    BigDecimal amount,
    String currency,
    String method,
    String transactionId,
    String qrData,
    String qrImageUrl,
    String paymentUrl,
    String pnrCode,
    PollingView polling,
    FailureView failure) {

  public record PollingView(Boolean shouldPoll, Integer nextPollAfterSeconds) {}

  public record FailureView(String code, String message) {}
}
