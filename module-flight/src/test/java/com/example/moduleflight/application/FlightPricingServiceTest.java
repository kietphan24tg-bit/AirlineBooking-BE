package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FlightPricingServiceTest {

  private final FlightPricingService service = new FlightPricingService();

  @Test
  void calculateTotalPrice_appliesChildAndInfantRates() {
    BigDecimal total = service.calculateTotalPrice(new BigDecimal("1500000.00"), 1, 1, 1);

    assertEquals(new BigDecimal("2775000.00"), total);
  }
}
