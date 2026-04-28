package com.example.appbootstrap.controller.payment.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentStatusResponse(
    Long id,
    Long bookingId,
    BigDecimal amount,
    String currency,
    String method,
    String status,
    OffsetDateTime paidAt,
    OffsetDateTime expireAt,
    String transactionId,
    String qrData,
    String qrImageUrl,
    String paymentUrl) {}
