package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.moduleflight.application.PromotionBatchPreviewService.PromoPreview;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class FlightRankingServiceTest {

  private final FlightRankingService service = new FlightRankingService();

  @Test
  void buildFareRankKey_cheapestUsesDiscountedPriceWhenPromoApplied() {
    FlightFareEntity discounted = fare(1L, 10L, "ECONOMY", 0, 0);
    FlightFareEntity regular = fare(2L, 11L, "ECONOMY", 0, 0);

    FlightRankKey discountedKey =
        service.buildFareRankKey(
            FlightSortBy.CHEAPEST,
            discounted,
            new PromoPreview(
                "APPLIED",
                "promo",
                new BigDecimal("1500000"),
                new BigDecimal("900000"),
                new BigDecimal("600000"),
                40),
            new BigDecimal("1500000"));
    FlightRankKey regularKey =
        service.buildFareRankKey(
            FlightSortBy.CHEAPEST,
            regular,
            PromoPreview.notApplicable("none", new BigDecimal("1000000")),
            new BigDecimal("1000000"));

    assertTrue(discountedKey.compareTo(regularKey) < 0);
  }

  @Test
  void buildFareRankKey_fastestUsesShorterDurationFirst() {
    FlightFareEntity fast = fare(1L, 10L, "ECONOMY", 0, 120);
    FlightFareEntity slow = fare(2L, 11L, "ECONOMY", 0, 240);

    FlightRankKey fastKey =
        service.buildFareRankKey(
            FlightSortBy.FASTEST,
            fast,
            PromoPreview.notApplicable("none", new BigDecimal("1000000")),
            new BigDecimal("1000000"));
    FlightRankKey slowKey =
        service.buildFareRankKey(
            FlightSortBy.FASTEST,
            slow,
            PromoPreview.notApplicable("none", new BigDecimal("800000")),
            new BigDecimal("800000"));

    assertTrue(fastKey.compareTo(slowKey) < 0);
  }

  @Test
  void buildFareRankKey_bestExperiencePrefersBetterCabinAndBenefits() {
    FlightFareEntity economy = fare(1L, 10L, "ECONOMY", 0, 120);
    FlightFareEntity business = fare(2L, 11L, "BUSINESS", 1, 150);

    FlightRankKey economyKey =
        service.buildFareRankKey(
            FlightSortBy.BEST_EXPERIENCE,
            economy,
            PromoPreview.notApplicable("none", new BigDecimal("800000")),
            new BigDecimal("800000"));
    FlightRankKey businessKey =
        service.buildFareRankKey(
            FlightSortBy.BEST_EXPERIENCE,
            business,
            PromoPreview.notApplicable("none", new BigDecimal("1200000")),
            new BigDecimal("1200000"));

    assertTrue(businessKey.compareTo(economyKey) < 0);
  }

  private static FlightFareEntity fare(
      long fareId, long flightId, String cabinClass, int premiumLevel, int durationMinutes) {
    AirlineEntity airline = new AirlineEntity();
    airline.setCode("VN");
    airline.setName("Vietnam Airlines");

    AirportEntity departure = new AirportEntity();
    departure.setCode("SGN");
    departure.setCountry("VN");
    AirportEntity arrival = new AirportEntity();
    arrival.setCode("HAN");
    arrival.setCountry("VN");

    FlightEntity flight = new FlightEntity();
    flight.setId(flightId);
    flight.setAirline(airline);
    flight.setFlightNumber("VN" + flightId);
    flight.setFlightDate(LocalDate.parse("2026-05-01"));
    flight.setDepartureAirport(departure);
    flight.setArrivalAirport(arrival);
    flight.setDepartureTime(OffsetDateTime.parse("2026-05-01T02:00:00Z"));
    flight.setArrivalTime(flight.getDepartureTime().plusMinutes(durationMinutes));
    flight.setStatus("SCHEDULED");

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setCode(cabinClass);
    fareClass.setName(cabinClass);
    fareClass.setCabinClass(cabinClass);
    fareClass.setBaggageCheckinKg(20 + premiumLevel * 10);
    fareClass.setBaggageHandKg(7 + premiumLevel * 3);
    fareClass.setMileageRate(BigDecimal.valueOf(1 + premiumLevel));
    fareClass.setRefundable(premiumLevel > 0);
    fareClass.setChangeable(true);
    fareClass.setPriorityCheckin(premiumLevel > 0);
    fareClass.setPriorityBoarding(premiumLevel > 0);
    fareClass.setLoungeAccess(premiumLevel > 0);
    fareClass.setSeatSelection(true);
    fareClass.setMealType("STANDARD");
    fareClass.setWifiIncluded(premiumLevel > 0);
    fareClass.setFastTrackSecurity(premiumLevel > 0);
    fareClass.setChangeFee(BigDecimal.ZERO);
    fareClass.setRefundFee(BigDecimal.ZERO);

    FlightFareEntity fare = new FlightFareEntity();
    fare.setId(fareId);
    fare.setFlight(flight);
    fare.setFareClass(fareClass);
    fare.setTotalPrice(new BigDecimal("1000000"));
    fare.setStatus("AVAILABLE");
    return fare;
  }
}
