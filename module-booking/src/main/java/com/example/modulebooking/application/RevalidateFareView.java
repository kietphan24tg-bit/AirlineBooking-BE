package com.example.modulebooking.application;

import java.math.BigDecimal;

public record RevalidateFareView(
    String revalidateId,
    long expiresInSeconds,
    Long flightId,
    Long fareId,
    String promoCode,
    PassengerSummaryView passengerSummary,
    PricingView pricing) {

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
}
