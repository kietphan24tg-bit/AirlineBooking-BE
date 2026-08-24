package com.example.moduleweb.controller.booking.dto;

import com.example.modulebooking.application.RevalidateFareView;
import java.math.BigDecimal;

public record RevalidateFareResponse(
    String revalidateId,
    long expiresInSeconds,
    Long flightId,
    Long fareId,
    String promoCode,
    PassengerSummaryResponse passengerSummary,
    PricingResponse pricing) {

  public static RevalidateFareResponse from(RevalidateFareView view) {
    return new RevalidateFareResponse(
        view.revalidateId(),
        view.expiresInSeconds(),
        view.flightId(),
        view.fareId(),
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
            view.pricing().discountedTotalPrice()));
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
}
