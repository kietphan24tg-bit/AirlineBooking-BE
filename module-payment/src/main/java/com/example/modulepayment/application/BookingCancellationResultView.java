package com.example.modulepayment.application;

import java.math.BigDecimal;
import java.util.List;

public record BookingCancellationResultView(
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
    List<ItemView> items,
    SummaryView summary) {

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
