package com.example.appbootstrap.controller.booking.dto;

import java.math.BigDecimal;

public record RevalidateFareResponse(
    String revalidateId,
    long expiresInSeconds,
    Long flightId,
    Long fareId,
    String promoCode,
    PassengerSummaryResponse passengerSummary,
    PricingResponse pricing) {

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
