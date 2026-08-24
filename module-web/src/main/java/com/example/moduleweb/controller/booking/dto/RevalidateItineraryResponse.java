package com.example.moduleweb.controller.booking.dto;

import com.example.modulebooking.application.RevalidateItineraryView;
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

  public static RevalidateItineraryResponse from(RevalidateItineraryView view) {
    return new RevalidateItineraryResponse(
        view.revalidateId(),
        view.expiresInSeconds(),
        view.tripType(),
        view.promoCode(),
        new PassengerSummaryResponse(
            view.passengerSummary().adult(),
            view.passengerSummary().child(),
            view.passengerSummary().infant()),
        new PricingResponse(
            view.pricing().currency(),
            view.pricing().baseFare(),
            view.pricing().taxes(),
            view.pricing().fees(),
            view.pricing().originalTotalPrice(),
            view.pricing().discountAmount(),
            view.pricing().discountPercent(),
            view.pricing().discountedTotalPrice()),
        view.segments().stream()
            .map(
                segment ->
                    new SegmentResponse(
                        segment.direction(),
                        segment.flightId(),
                        segment.fareId(),
                        segment.fareClassId(),
                        segment.fareCode(),
                        segment.fareName(),
                        segment.cabinClass(),
                        segment.currency(),
                        new PricingResponse(
                            segment.pricing().currency(),
                            segment.pricing().baseFare(),
                            segment.pricing().taxes(),
                            segment.pricing().fees(),
                            segment.pricing().originalTotalPrice(),
                            segment.pricing().discountAmount(),
                            segment.pricing().discountPercent(),
                            segment.pricing().discountedTotalPrice())))
            .toList());
  }

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
