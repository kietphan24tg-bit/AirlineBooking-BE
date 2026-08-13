package com.example.moduleweb.controller.booking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record SeatChangePreviewResponse(
    Long bookingId,
    String currency,
    String previewToken,
    String settlementType,
    List<ItemResponse> items,
    SummaryResponse summary) {

  public record ItemResponse(
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

  public record SummaryResponse(
      BigDecimal additionalAmount, BigDecimal refundAmount, BigDecimal netAmount) {}
}
