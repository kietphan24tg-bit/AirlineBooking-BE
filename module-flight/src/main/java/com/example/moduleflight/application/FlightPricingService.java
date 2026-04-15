package com.example.moduleflight.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class FlightPricingService {

  private static final BigDecimal CHILD_RATE = new BigDecimal("0.75");
  private static final BigDecimal INFANT_RATE = new BigDecimal("0.10");

  public BigDecimal calculateTotalPrice(
      BigDecimal oneAdultFareTotalPrice, int adultCount, int childCount, int infantCount) {
    BigDecimal adultTotal = oneAdultFareTotalPrice.multiply(BigDecimal.valueOf(adultCount));
    BigDecimal childTotal =
        oneAdultFareTotalPrice.multiply(CHILD_RATE).multiply(BigDecimal.valueOf(childCount));
    BigDecimal infantTotal =
        oneAdultFareTotalPrice.multiply(INFANT_RATE).multiply(BigDecimal.valueOf(infantCount));
    return adultTotal.add(childTotal).add(infantTotal).setScale(2, RoundingMode.HALF_UP);
  }
}
