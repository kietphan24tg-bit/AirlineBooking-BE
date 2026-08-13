package com.example.moduleweb.controller.booking.dto;

import java.math.BigDecimal;
import java.util.List;

public record RevalidateItineraryResponse(
    String revalidateId,
    long expiresInSeconds,
    String tripType,
    String promoCode,
    PassengerSummaryResponse passengerSummary,
    PricingResponse pricing,
    List<SegmentResponse> segments) {

  public record PassengerSummaryResponse(int adult, int child, int infant) {}

  public record PricingResponse(
      String currency,
      BigDecimal baseFare,
      BigDecimal taxes,
      BigDecimal fees,
      BigDecimal originalTotalPrice,
      BigDecimal discountAmount,
      Integer discountPercent,
      BigDecimal discountedTotalPrice) {}

  public record SegmentResponse(
      String direction,
      Long flightId,
      Long fareId,
      Long fareClassId,
      String fareCode,
      String fareName,
      String cabinClass,
      String currency,
      PricingResponse pricing) {}
}
