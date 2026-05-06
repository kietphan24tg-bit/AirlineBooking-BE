package com.example.appbootstrap.controller.booking.dto;

import java.math.BigDecimal;
import java.util.List;

public record BookingCancellationResponse(
    Long bookingId,
    String pnrCode,
    String contactEmail,
    Long cancellationId,
    String refundStatus,
    String provider,
    String providerRefundId,
    String bookingStatus,
    String paymentStatus,
    String currency,
    String refundEstimateTitle,
    String refundEstimateMessage,
    String refundEstimateNote,
    List<ItemResponse> items,
    SummaryResponse summary) {

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
