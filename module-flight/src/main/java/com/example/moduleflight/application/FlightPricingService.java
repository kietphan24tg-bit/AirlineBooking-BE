package com.example.moduleflight.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class FlightPricingService {

  private static final BigDecimal CHILD_RATE = new BigDecimal("0.75");
  private static final BigDecimal INFANT_RATE = new BigDecimal("0.10");

  public BigDecimal calculateTotalPrice(
      BigDecimal oneAdultFareTotalPrice, int adultCount, int childCount, int infantCount) {
    Objects.requireNonNull(oneAdultFareTotalPrice, "oneAdultFareTotalPrice must not be null");
    if (oneAdultFareTotalPrice.signum() < 0) {
      throw new IllegalArgumentException("oneAdultFareTotalPrice must not be negative");
    }
    if (adultCount < 0) {
      throw new IllegalArgumentException("adultCount must not be negative");
    }
    if (childCount < 0) {
      throw new IllegalArgumentException("childCount must not be negative");
    }
    if (infantCount < 0) {
      throw new IllegalArgumentException("infantCount must not be negative");
    }
    BigDecimal adultTotal = oneAdultFareTotalPrice.multiply(BigDecimal.valueOf(adultCount));
    BigDecimal childTotal =
        oneAdultFareTotalPrice.multiply(CHILD_RATE).multiply(BigDecimal.valueOf(childCount));
    BigDecimal infantTotal =
        oneAdultFareTotalPrice.multiply(INFANT_RATE).multiply(BigDecimal.valueOf(infantCount));
    return adultTotal.add(childTotal).add(infantTotal).setScale(2, RoundingMode.HALF_UP);
  }
}
