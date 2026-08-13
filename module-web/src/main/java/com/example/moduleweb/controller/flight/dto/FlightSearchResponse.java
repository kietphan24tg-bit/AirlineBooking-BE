package com.example.moduleweb.controller.flight.dto;

import com.example.moduleflight.application.FlightSearchResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record FlightSearchResponse(
    List<FlightSearchItemResponse> outbound,
    List<FlightSearchItemResponse> inbound,
    PageInfoResponse outboundPageInfo,
    PageInfoResponse inboundPageInfo,
    PriceRangeResponse outboundPriceRange,
    PriceRangeResponse inboundPriceRange) {

  public static FlightSearchResponse from(FlightSearchResult result) {
    return new FlightSearchResponse(
        result.outbound().stream().map(FlightSearchItemResponse::from).toList(),
        result.inbound().stream().map(FlightSearchItemResponse::from).toList(),
        PageInfoResponse.from(result.outboundPageInfo()),
        PageInfoResponse.from(result.inboundPageInfo()),
        PriceRangeResponse.from(result.outboundPriceRange()),
        PriceRangeResponse.from(result.inboundPriceRange()));
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
      boolean selectable,
      BigDecimal originalTotalPrice,
      BigDecimal discountedTotalPrice,
      BigDecimal discountAmount,
      Integer discountPercent,
      String promoStatus,
      String promoMessage,
      FareClassBenefitsResponse fareClassBenefits) {
    public static FareOptionResponse from(FlightSearchResult.FareOptionView view) {
      return new FareOptionResponse(
          view.fareId(),
          view.fareCode(),
          view.cabinClass(),
          view.fareStatus(),
          view.selectable(),
          view.originalTotalPrice(),
          view.discountedTotalPrice(),
          view.discountAmount(),
          view.discountPercent(),
          view.promoStatus(),
          view.promoMessage(),
          FareClassBenefitsResponse.from(view.fareClassBenefits()));
    }
  }

  public record FareClassBenefitsResponse(
      Long fareClassId,
      String fareClassName,
      String fareClassCode,
      Integer baggageCheckinKg,
      Integer baggageHandKg,
      BigDecimal changeFee,
      BigDecimal refundFee,
      boolean refundable,
      boolean changeable,
      boolean priorityCheckin,
      boolean priorityBoarding,
      BigDecimal mileageRate,
      boolean loungeAccess,
      boolean seatSelection,
      String seatSelectionPolicy,
      String mealType,
      boolean wifiIncluded,
      boolean fastTrackSecurity) {
    static FareClassBenefitsResponse from(FlightSearchResult.FareClassBenefitsView view) {
      if (view == null) {
        return null;
      }
      return new FareClassBenefitsResponse(
          view.fareClassId(),
          view.fareClassName(),
          view.fareClassCode(),
          view.baggageCheckinKg(),
          view.baggageHandKg(),
          view.changeFee(),
          view.refundFee(),
          view.refundable(),
          view.changeable(),
          view.priorityCheckin(),
          view.priorityBoarding(),
          view.mileageRate(),
          view.loungeAccess(),
          view.seatSelection(),
          view.seatSelectionPolicy(),
          view.mealType(),
          view.wifiIncluded(),
          view.fastTrackSecurity());
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

  public record PriceRangeResponse(BigDecimal minPrice, BigDecimal maxPrice, String currency) {
    static PriceRangeResponse from(FlightSearchResult.PriceRange range) {
      if (range == null) {
        return null;
      }
      return new PriceRangeResponse(range.minPrice(), range.maxPrice(), range.currency());
    }
  }
}
