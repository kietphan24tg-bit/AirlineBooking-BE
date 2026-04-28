package com.example.modulepayment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Read model for payment status (e.g. result page after PayOS redirect, FE polling).
 *
 * @param paymentUrl Checkout URL from provider; not persisted today — typically null unless
 *     populated from stored data in a future change.
 */
public record PaymentStatusView(
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
