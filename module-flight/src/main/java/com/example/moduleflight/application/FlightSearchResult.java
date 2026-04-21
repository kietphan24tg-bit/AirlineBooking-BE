package com.example.moduleflight.application;

import com.example.moduleflight.entity.FareClassEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record FlightSearchResult(
    List<FlightOptionView> outbound,
    List<FlightOptionView> inbound,
    PageInfo outboundPageInfo,
    PageInfo inboundPageInfo,
    PriceRange outboundPriceRange,
    PriceRange inboundPriceRange) {

  /**
   * Min/max effective fare prices on the current result page (after pagination). Uses discounted
   * price when present, otherwise original. Returns {@code null} when there is no priced fare.
   */
  public static PriceRange priceRangeFromFlights(List<FlightOptionView> flights) {
    if (flights == null || flights.isEmpty()) {
      return null;
    }
    BigDecimal min = null;
    BigDecimal max = null;
    String currency = null;
    for (FlightOptionView flight : flights) {
      for (FareOptionView fare : flight.fares()) {
        BigDecimal effective = effectiveFarePrice(fare);
        if (effective == null) {
          continue;
        }
        if (currency == null) {
          currency = flight.currency();
        }
        if (min == null || effective.compareTo(min) < 0) {
          min = effective;
        }
        if (max == null || effective.compareTo(max) > 0) {
          max = effective;
        }
      }
    }
    if (min == null) {
      return null;
    }
    return new PriceRange(min, max, currency);
  }

  private static BigDecimal effectiveFarePrice(FareOptionView fare) {
    if (fare.discountedTotalPrice() != null) {
      return fare.discountedTotalPrice();
    }
    return fare.originalTotalPrice();
  }

  public record PriceRange(BigDecimal minPrice, BigDecimal maxPrice, String currency) {}

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
      boolean selectable,
      BigDecimal originalTotalPrice,
      BigDecimal discountedTotalPrice,
      BigDecimal discountAmount,
      Integer discountPercent,
      String promoStatus,
      String promoMessage,
      FareClassBenefitsView fareClassBenefits) {}

  /**
   * Structured fare-class policy from {@code fare_classes} for UI (i18n can format labels from
   * these fields).
   */
  public record FareClassBenefitsView(
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
      String mealType,
      boolean wifiIncluded,
      boolean fastTrackSecurity) {

    public static FareClassBenefitsView fromEntity(FareClassEntity fc) {
      if (fc == null) {
        return null;
      }
      return new FareClassBenefitsView(
          fc.getId(),
          fc.getName(),
          fc.getCode(),
          fc.getBaggageCheckinKg(),
          fc.getBaggageHandKg(),
          fc.getChangeFee(),
          fc.getRefundFee(),
          fc.isRefundable(),
          fc.isChangeable(),
          fc.isPriorityCheckin(),
          fc.isPriorityBoarding(),
          fc.getMileageRate(),
          fc.isLoungeAccess(),
          fc.isSeatSelection(),
          fc.getMealType(),
          fc.isWifiIncluded(),
          fc.isFastTrackSecurity());
    }
  }

  public record PageInfo(String nextCursor, boolean hasMore, int limit) {}
}
