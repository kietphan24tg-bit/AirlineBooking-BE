package com.example.moduleflight.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record FlightSearchResult(
    List<FlightOptionView> outbound,
    List<FlightOptionView> inbound,
    PageInfo outboundPageInfo,
    PageInfo inboundPageInfo) {

  public record FlightOptionView(
      Long flightId,
      String flightNumber,
      String airlineCode,
      String airlineName,
      String fromAirportCode,
      String toAirportCode,
      LocalDate flightDate,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime,
      String currency,
      List<FareOptionView> fares) {}

  public record FareOptionView(
      Long fareId,
      String fareCode,
      String cabinClass,
      String fareStatus,
      BigDecimal originalTotalPrice,
      BigDecimal discountedTotalPrice,
      BigDecimal discountAmount,
      Integer discountPercent,
      String promoStatus,
      String promoMessage) {}

  public record PageInfo(String nextCursor, boolean hasMore, int limit) {}
}
