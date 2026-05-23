package com.example.modulepayment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingCancellationResultView(
    Long bookingId,
    String pnrCode,
    String contactEmail,
    Long cancellationId,
    String cancellationCode,
    OffsetDateTime cancelledAt,
    String refundStatus,
    String provider,
    String providerRefundId,
    String bookingStatus,
    String paymentStatus,
    String currency,
    String refundEstimateTitle,
    String refundEstimateMessage,
    String refundEstimateNote,
    RefundMethodView refundMethod,
    List<ItemView> items,
    SummaryView summary) {

  public record RefundMethodView(
      String type, String label, String maskedAccount, String estimatedDays) {}

  public record ItemView(
      Long ticketId,
      Long passengerId,
      String passengerType,
      String passengerName,
      String ticketStatus,
      BigDecimal fareGross,
      BigDecimal promoAllocated,
      BigDecimal fareNet,
      BigDecimal seatRefund,
      BigDecimal serviceRefund,
      BigDecimal refundFee,
      BigDecimal netRefund) {}

  public record SummaryView(
      BigDecimal fareGross,
      BigDecimal promoAllocated,
      BigDecimal fareNet,
      BigDecimal seatRefund,
      BigDecimal serviceRefund,
      BigDecimal refundFee,
      BigDecimal netRefund) {}
}
