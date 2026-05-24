package com.example.modulepayment.application;

import java.math.BigDecimal;

public record SeatChangeConfirmView(
    Long settlementId,
    Long bookingId,
    String currency,
    String settlementType,
    String status,
    String refundStatus,
    String provider,
    String providerRefundId,
    String errorCode,
    String errorMessage,
    RefundMethodView refundMethod,
    java.util.List<PaymentMethodItemView> paymentMethods,
    SummaryView summary) {

  public record RefundMethodView(
      String type, String label, String maskedAccount, String estimatedDays) {}

  public record PaymentMethodItemView(
      String code, String label, String provider, String icon, boolean supported) {}

  public record SummaryView(
      BigDecimal additionalAmount, BigDecimal refundAmount, BigDecimal netAmount) {}
}
