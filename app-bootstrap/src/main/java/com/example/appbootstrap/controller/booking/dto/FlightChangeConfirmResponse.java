package com.example.appbootstrap.controller.booking.dto;

import java.math.BigDecimal;
import java.util.List;

public record FlightChangeConfirmResponse(
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
    RefundMethodResponse refundMethod,
    List<PaymentMethodItemResponse> paymentMethods,
    SummaryResponse summary) {

  public record RefundMethodResponse(
      String type, String label, String maskedAccount, String estimatedDays) {}

  public record PaymentMethodItemResponse(
      String code, String label, String provider, String icon, boolean supported) {}

  public record SummaryResponse(
      BigDecimal additionalAmount, BigDecimal refundAmount, BigDecimal netAmount) {}
}
