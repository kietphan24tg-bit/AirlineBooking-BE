package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlightSearchResultTest {

  @Test
  void priceRangeFromFlights_empty_returnsNull() {
    assertNull(FlightSearchResult.priceRangeFromFlights(List.of()));
    assertNull(FlightSearchResult.priceRangeFromFlights(null));
  }

  @Test
  void priceRangeFromFlights_noPricedFares_returnsNull() {
    FlightSearchResult.FlightOptionView flight =
        new FlightSearchResult.FlightOptionView(
            1L,
            "VN1",
            "VN",
            "Air",
            "SGN",
            "HAN",
            LocalDate.parse("2026-06-01"),
            OffsetDateTime.parse("2026-06-01T02:00:00Z"),
            OffsetDateTime.parse("2026-06-01T04:00:00Z"),
            "VND",
            List.of());
    assertNull(FlightSearchResult.priceRangeFromFlights(List.of(flight)));
  }

  @Test
  void priceRangeFromFlights_usesDiscountedWhenPresent_andMinMaxAcrossFares() {
    FlightSearchResult.FareOptionView cheap =
        new FlightSearchResult.FareOptionView(
            1L,
            "Y",
            "ECONOMY",
            "AVAILABLE",
            true,
            new BigDecimal("2000000"),
            new BigDecimal("1500000"),
            BigDecimal.ZERO,
            25,
            "APPLIED",
            "ok",
            null);
    FlightSearchResult.FareOptionView expensive =
        new FlightSearchResult.FareOptionView(
            2L,
            "J",
            "BUSINESS",
            "AVAILABLE",
            true,
            new BigDecimal("5000000"),
            null,
            BigDecimal.ZERO,
            null,
            "NOT_APPLICABLE",
            "",
            null);
    FlightSearchResult.FlightOptionView view =
        new FlightSearchResult.FlightOptionView(
            10L,
            "VN2",
            "VN",
            "Air",
            "SGN",
            "HAN",
            LocalDate.parse("2026-06-01"),
            OffsetDateTime.parse("2026-06-01T02:00:00Z"),
            OffsetDateTime.parse("2026-06-01T04:00:00Z"),
            "VND",
            List.of(cheap, expensive));

    FlightSearchResult.PriceRange range = FlightSearchResult.priceRangeFromFlights(List.of(view));
    assertEquals(new BigDecimal("1500000"), range.minPrice());
    assertEquals(new BigDecimal("5000000"), range.maxPrice());
    assertEquals("VND", range.currency());
  }
}
