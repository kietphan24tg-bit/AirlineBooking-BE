package com.example.modulepayment.application;

import java.math.BigDecimal;
import java.util.List;

public record BookingCancellationPreviewView(
    Long bookingId,
    String bookingStatus,
    String paymentStatus,
    String currency,
    List<ItemView> items,
    SummaryView summary) {

  public record ItemView(
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

  public record SummaryView(
      BigDecimal fareGross,
      BigDecimal promoAllocated,
      BigDecimal fareNet,
      BigDecimal seatRefund,
      BigDecimal serviceRefund,
      BigDecimal refundFee,
      BigDecimal netRefund) {}
}
