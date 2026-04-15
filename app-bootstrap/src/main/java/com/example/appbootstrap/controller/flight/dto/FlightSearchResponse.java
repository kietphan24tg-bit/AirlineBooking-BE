package com.example.appbootstrap.controller.flight.dto;

import com.example.moduleflight.application.FlightSearchResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record FlightSearchResponse(
    List<FlightSearchItemResponse> outbound,
    List<FlightSearchItemResponse> inbound,
    PageInfoResponse outboundPageInfo,
    PageInfoResponse inboundPageInfo) {

  public static FlightSearchResponse from(FlightSearchResult result) {
    return new FlightSearchResponse(
        result.outbound().stream().map(FlightSearchItemResponse::from).toList(),
        result.inbound().stream().map(FlightSearchItemResponse::from).toList(),
        PageInfoResponse.from(result.outboundPageInfo()),
        PageInfoResponse.from(result.inboundPageInfo()));
  }

  public record FlightSearchItemResponse(
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
      List<FareOptionResponse> fares) {
    public static FlightSearchItemResponse from(FlightSearchResult.FlightOptionView view) {
      return new FlightSearchItemResponse(
          view.flightId(),
          view.flightNumber(),
          view.airlineCode(),
          view.airlineName(),
          view.fromAirportCode(),
          view.toAirportCode(),
          view.flightDate(),
          view.departureTime(),
          view.arrivalTime(),
          view.currency(),
          view.fares().stream().map(FareOptionResponse::from).toList());
    }
  }

  public record FareOptionResponse(
      Long fareId,
      String fareCode,
      String cabinClass,
      String fareStatus,
      BigDecimal originalTotalPrice,
      BigDecimal discountedTotalPrice,
      BigDecimal discountAmount,
      Integer discountPercent,
      String promoStatus,
      String promoMessage) {
    public static FareOptionResponse from(FlightSearchResult.FareOptionView view) {
      return new FareOptionResponse(
          view.fareId(),
          view.fareCode(),
          view.cabinClass(),
          view.fareStatus(),
          view.originalTotalPrice(),
          view.discountedTotalPrice(),
          view.discountAmount(),
          view.discountPercent(),
          view.promoStatus(),
          view.promoMessage());
    }
  }

  public record PageInfoResponse(String nextCursor, boolean hasMore, int limit) {
    public static PageInfoResponse from(FlightSearchResult.PageInfo pageInfo) {
      if (pageInfo == null) {
        return null;
      }
      return new PageInfoResponse(pageInfo.nextCursor(), pageInfo.hasMore(), pageInfo.limit());
    }
  }
}
