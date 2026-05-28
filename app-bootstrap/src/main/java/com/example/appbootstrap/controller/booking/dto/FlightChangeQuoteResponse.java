package com.example.appbootstrap.controller.booking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record FlightChangeQuoteResponse(
    Long bookingId,
    String currency,
    String quoteToken,
    OffsetDateTime quoteExpiresAt,
    boolean eligible,
    String settlementType,
    FlightSummaryResponse oldFlight,
    FlightSummaryResponse newFlight,
    List<ItemResponse> items,
    SummaryResponse summary,
    ImpactsResponse impacts,
    List<String> messages) {

  public record FlightSummaryResponse(
      Long flightId, String flightNumber, String from, String to, OffsetDateTime departureTime) {}

  public record ItemResponse(
      Long ticketId,
      Long passengerId,
      String passengerName,
      String passengerType,
      boolean eligible,
      String reason,
      BigDecimal originalFareAmount,
      BigDecimal newFareAmount,
      BigDecimal fareDifference,
      BigDecimal changeFee,
      BigDecimal taxDifference,
      BigDecimal netAmount) {}

  public record SummaryResponse(
      BigDecimal originalFareAmount,
      BigDecimal newFareAmount,
      BigDecimal fareDifference,
      BigDecimal changeFee,
      BigDecimal taxDifference,
      BigDecimal refundFee,
      BigDecimal netAmount) {}

  public record ImpactsResponse(String seatAction, String serviceAction) {}
}
