package com.example.appbootstrap.controller.booking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingCancellationResponse(
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
    RefundMethodResponse refundMethod,
    List<ItemResponse> items,
    SummaryResponse summary) {

  public record RefundMethodResponse(
      String type, String label, String maskedAccount, String estimatedDays) {}

  public record ItemResponse(
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

  public record SummaryResponse(
      BigDecimal fareGross,
      BigDecimal promoAllocated,
      BigDecimal fareNet,
      BigDecimal seatRefund,
      BigDecimal serviceRefund,
      BigDecimal refundFee,
      BigDecimal netRefund) {}
}
