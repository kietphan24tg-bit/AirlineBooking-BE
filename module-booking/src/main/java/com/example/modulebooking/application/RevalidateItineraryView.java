package com.example.modulebooking.application;

import java.math.BigDecimal;
import java.util.List;

public record RevalidateItineraryView(
    String revalidateId,
    long expiresInSeconds,
    String tripType,
    String promoCode,
    PassengerSummaryView passengerSummary,
    PricingView pricing,
    List<SegmentView> segments) {

  public record PassengerSummaryView(int adult, int child, int infant) {}

  public record PricingView(
      String currency,
      BigDecimal baseFare,
      BigDecimal taxes,
      BigDecimal fees,
      BigDecimal originalTotalPrice,
      BigDecimal discountAmount,
      Integer discountPercent,
      BigDecimal discountedTotalPrice) {}

  public record SegmentView(
      String direction,
      Long flightId,
      Long fareId,
      Long fareClassId,
      String fareCode,
      String fareName,
      String cabinClass,
      String currency,
      PricingView pricing) {}
}
