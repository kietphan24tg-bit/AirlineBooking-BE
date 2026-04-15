package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightSearchServiceTest {

  @Mock private FlightQueryService flightQueryService;
  @Mock private FlightPricingService flightPricingService;
  @Mock private PromotionBatchPreviewService promotionBatchPreviewService;
  @Mock private FlightRankingService flightRankingService;
  @Mock private FlightPaginationService flightPaginationService;
  @Mock private CountryCurrencyResolver countryCurrencyResolver;

  private FlightSearchFacadeService service;

  @BeforeEach
  void setUp() {
    service =
        new FlightSearchFacadeService(
            flightQueryService,
            flightPricingService,
            promotionBatchPreviewService,
            flightRankingService,
            flightPaginationService,
            countryCurrencyResolver);
  }

  @Test
  void search_roundTripUsesIndependentDirectionPagination() {
    FlightFareEntity outboundFare = fare(1L, 1001L, "VN123", "AVAILABLE", "ECONOMY", "Vietnam");
    outboundFare.setTotalPrice(new BigDecimal("1500000"));
    FlightFareEntity inboundFare = fare(2L, 1002L, "VN456", "AVAILABLE", "ECONOMY", "Vietnam");
    inboundFare.setTotalPrice(new BigDecimal("1400000"));

    when(promotionBatchPreviewService.loadContext(anyString()))
        .thenReturn(PromotionBatchPreviewService.PromotionContext.withMessage("NOT_APPLICABLE", "none"));
    when(flightQueryService.findByRouteAndDate("SGN", "HAN", LocalDate.parse("2026-05-01")))
        .thenReturn(List.of(List.of(outboundFare)));
    when(flightQueryService.findByRouteAndDate("HAN", "SGN", LocalDate.parse("2026-05-10")))
        .thenReturn(List.of(List.of(inboundFare)));
    when(countryCurrencyResolver.resolveCountryCode(any())).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(flightPricingService.calculateTotalPrice(any(), any(Integer.class), any(Integer.class), any(Integer.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(promotionBatchPreviewService.preview(any(), any(), anyString(), anyString(), any(Boolean.class)))
        .thenReturn(
            new PromotionBatchPreviewService.PromoPreview(
                "APPLIED",
                "Promo applied successfully",
                new BigDecimal("1500000"),
                new BigDecimal("1200000"),
                new BigDecimal("300000"),
                20));
    when(flightRankingService.buildFareRankKey(any(), any(), any()))
        .thenReturn(
            new FlightRankKey(
                0, new BigDecimal("1200000"), OffsetDateTime.parse("2026-05-01T02:00:00Z"), 1001L, 1L))
        .thenReturn(
            new FlightRankKey(
                0, new BigDecimal("1200000"), OffsetDateTime.parse("2026-05-01T02:00:00Z"), 1002L, 2L));
    when(flightPaginationService.paginate(any(), any(Integer.class), any()))
        .thenReturn(
            new FlightPaginationService.PageResult<>(
                List.of(
                    new TestRankedFlightOption(
                        new FlightSearchResult.FlightOptionView(
                            1001L,
                            "VN123",
                            "VN",
                            "Vietnam Airlines",
                            "SGN",
                            "HAN",
                            LocalDate.parse("2026-05-01"),
                            OffsetDateTime.parse("2026-05-01T02:00:00Z"),
                            OffsetDateTime.parse("2026-05-01T04:00:00Z"),
                            "VND",
                            List.of()),
                        new FlightRankKey(
                            0,
                            new BigDecimal("1200000"),
                            OffsetDateTime.parse("2026-05-01T02:00:00Z"),
                            1001L,
                            1L))),
                "v1|outbound",
                true,
                1))
        .thenReturn(
            new FlightPaginationService.PageResult<>(
                List.of(
                    new TestRankedFlightOption(
                        new FlightSearchResult.FlightOptionView(
                            1002L,
                            "VN456",
                            "VN",
                            "Vietnam Airlines",
                            "HAN",
                            "SGN",
                            LocalDate.parse("2026-05-10"),
                            OffsetDateTime.parse("2026-05-10T02:00:00Z"),
                            OffsetDateTime.parse("2026-05-10T04:00:00Z"),
                            "VND",
                            List.of()),
                        new FlightRankKey(
                            0,
                            new BigDecimal("1200000"),
                            OffsetDateTime.parse("2026-05-10T02:00:00Z"),
                            1002L,
                            2L))),
                "v1|inbound",
                true,
                1));

    FlightSearchResult result =
        service.search(
            new FlightSearchQuery(
                FlightSearchTripType.ROUND_TRIP,
                "SGN",
                "HAN",
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-10"),
                1,
                0,
                0,
                "SAVE20",
                null,
                "v1|outbound-cursor",
                "v1|inbound-cursor",
                20));

    assertEquals(1, result.outbound().size());
    assertEquals(1, result.inbound().size());
    assertNotNull(result.outboundPageInfo().nextCursor());
    assertNotNull(result.inboundPageInfo().nextCursor());
  }

  private static FlightFareEntity fare(
      Long fareId,
      Long flightId,
      String flightNumber,
      String fareStatus,
      String fareCode,
      String country) {
    AirlineEntity airline = new AirlineEntity();
    airline.setCode("VN");
    airline.setName("Vietnam Airlines");

    AirportEntity departure = new AirportEntity();
    departure.setCode("SGN");
    departure.setCountry(country);
    AirportEntity arrival = new AirportEntity();
    arrival.setCode("HAN");
    arrival.setCountry("Vietnam");

    FlightEntity flight = new FlightEntity();
    flight.setId(flightId);
    flight.setAirline(airline);
    flight.setFlightNumber(flightNumber);
    flight.setFlightDate(LocalDate.parse("2026-05-01"));
    flight.setDepartureAirport(departure);
    flight.setArrivalAirport(arrival);
    flight.setDepartureTime(OffsetDateTime.parse("2026-05-01T02:00:00Z"));
    flight.setArrivalTime(OffsetDateTime.parse("2026-05-01T04:00:00Z"));
    flight.setStatus("SCHEDULED");

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setCode(fareCode);
    fareClass.setCabinClass(fareCode);

    FlightFareEntity fare = new FlightFareEntity();
    fare.setId(fareId);
    fare.setFlight(flight);
    fare.setFareClass(fareClass);
    fare.setBasePrice(new BigDecimal("1000000"));
    fare.setTotalTaxAmount(new BigDecimal("100000"));
    fare.setTotalPrice(new BigDecimal("1100000"));
    fare.setStatus(fareStatus);
    fare.setUpdatedAt(OffsetDateTime.now());
    return fare;
  }

  private record TestRankedFlightOption(FlightSearchResult.FlightOptionView view, FlightRankKey rankKey)
      implements FlightPaginationService.HasRankKey {}
}
