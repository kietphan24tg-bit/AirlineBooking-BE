package com.example.modulepayment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record SeatChangePreviewView(
    Long bookingId,
    String currency,
    String previewToken,
    String settlementType,
    List<ItemView> items,
    SummaryView summary) {

  public record ItemView(
      Long passengerId,
      String passengerName,
      Long flightId,
      String flightNumber,
      String departureAirportCode,
      String arrivalAirportCode,
      OffsetDateTime departureTime,
      Long fromSeatId,
      String fromSeatNumber,
      Long toSeatId,
      String toSeatNumber,
      BigDecimal oldAmount,
      BigDecimal newAmount,
      BigDecimal delta,
      String status,
      String message) {}

  public record SummaryView(
      BigDecimal additionalAmount, BigDecimal refundAmount, BigDecimal netAmount) {}
}
