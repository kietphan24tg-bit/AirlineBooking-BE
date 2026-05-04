package com.example.appbootstrap.controller.booking.dto;

import java.math.BigDecimal;
import java.util.List;

public record BookingCancellationPreviewResponse(
    Long bookingId,
    String bookingStatus,
    String paymentStatus,
    String currency,
    List<ItemResponse> items,
    SummaryResponse summary) {

  public record ItemResponse(
      Long ticketId,
      Long passengerId,
      String passengerType,
      String passengerName,
      boolean eligible,
      String reason,
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
