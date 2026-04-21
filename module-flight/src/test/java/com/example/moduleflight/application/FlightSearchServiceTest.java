package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleflight.application.PromotionBatchPreviewService.PromoPreview;
import com.example.moduleflight.application.PromotionBatchPreviewService.PromotionContext;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightSearchServiceTest {

  @Mock private FlightQueryService flightQueryService;
  @Mock private FlightPricingService flightPricingService;
  @Mock private PromotionBatchPreviewService promotionBatchPreviewService;
  @Mock private FlightRankingService flightRankingService;
  @Mock private FlightPaginationService flightPaginationService;
  @Mock private FlightInventoryAvailabilityService flightInventoryAvailabilityService;
  @Mock private CountryCurrencyResolver countryCurrencyResolver;
  @Mock private FlightSearchFallbackService flightSearchFallbackService;

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
            flightInventoryAvailabilityService,
            countryCurrencyResolver,
            flightSearchFallbackService);
    when(flightInventoryAvailabilityService.hasEnoughSeats(anyLong(), anyLong(), anyInt()))
        .thenReturn(true);
  }

  @Test
  void search_roundTripUsesIndependentDirectionPagination() {
    FlightFareEntity outboundFare = fare(1L, 1001L, "VN123", "AVAILABLE", "ECONOMY", "Vietnam");
    outboundFare.setTotalPrice(new BigDecimal("1500000"));
    FlightFareEntity inboundFare = fare(2L, 1002L, "VN456", "AVAILABLE", "ECONOMY", "Vietnam");
    inboundFare.setTotalPrice(new BigDecimal("1400000"));

    when(promotionBatchPreviewService.loadContext(anyString()))
        .thenReturn(
            PromotionBatchPreviewService.PromotionContext.withMessage("NOT_APPLICABLE", "none"));
    when(flightQueryService.findByRouteAndDate("SGN", "HAN", LocalDate.parse("2026-05-01")))
        .thenReturn(List.of(List.of(outboundFare)));
    when(flightQueryService.findByRouteAndDate("HAN", "SGN", LocalDate.parse("2026-05-10")))
        .thenReturn(List.of(List.of(inboundFare)));
    when(countryCurrencyResolver.resolveCountryCode(any())).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(flightPricingService.calculateTotalPrice(any(), anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(promotionBatchPreviewService.preview(any(), any(), anyString(), anyString(), anyBoolean()))
        .thenReturn(
            new PromotionBatchPreviewService.PromoPreview(
                "APPLIED",
                "Promo applied successfully",
                new BigDecimal("1500000"),
                new BigDecimal("1200000"),
                new BigDecimal("300000"),
                20));
    when(flightRankingService.buildFareRankKey(any(), any(), any(), any()))
        .thenReturn(
            recommendedKey(
                0,
                new BigDecimal("1200000"),
                OffsetDateTime.parse("2026-05-01T02:00:00Z"),
                1001L,
                1L))
        .thenReturn(
            recommendedKey(
                0,
                new BigDecimal("1200000"),
                OffsetDateTime.parse("2026-05-01T02:00:00Z"),
                1002L,
                2L));
    when(flightPaginationService.paginate(any(), anyInt(), any()))
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
                        recommendedKey(
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
                        recommendedKey(
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
                20,
                Collections.emptySet(),
                null,
                null,
                null,
                FlightConnectionFilter.ANY,
                FlightSortBy.RECOMMENDED));

    assertEquals(1, result.outbound().size());
    assertEquals(1, result.inbound().size());
    assertNotNull(result.outboundPageInfo().nextCursor());
    assertNotNull(result.inboundPageInfo().nextCursor());
    assertNull(result.outboundPriceRange());
    assertNull(result.inboundPriceRange());
  }

  @Test
  void search_cabinFilter_keepsOnlyFaresInSelectedCabins() {
    List<FlightFareEntity> group = economyAndBusinessOnSameFlight();
    when(promotionBatchPreviewService.loadContext(any())).thenReturn(PromotionContext.empty());
    when(flightQueryService.findByRouteAndDate("SGN", "HAN", LocalDate.parse("2026-05-01")))
        .thenReturn(List.of(group));
    when(countryCurrencyResolver.resolveCountryCode(any())).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(flightPricingService.calculateTotalPrice(any(), anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(promotionBatchPreviewService.preview(any(), any(), anyString(), anyString(), anyBoolean()))
        .thenReturn(PromoPreview.notApplicable("n", new BigDecimal("1000000")))
        .thenReturn(PromoPreview.notApplicable("n", new BigDecimal("5000000")));
    OffsetDateTime dep = group.getFirst().getFlight().getDepartureTime();
    when(flightRankingService.buildFareRankKey(any(), any(), any(), any()))
        .thenReturn(recommendedKey(2, new BigDecimal("1000000"), dep, 100L, 1L))
        .thenReturn(recommendedKey(2, new BigDecimal("5000000"), dep, 100L, 2L));
    when(flightPaginationService.paginate(any(), anyInt(), any()))
        .thenAnswer(
            inv ->
                new FlightPaginationService.PageResult<>(
                    inv.getArgument(0), null, false, inv.getArgument(1)));

    FlightSearchResult result =
        service.search(baseQueryBuilder().cabinClasses(Set.of(CabinClass.BUSINESS)).build());

    assertEquals(1, result.outbound().size());
    assertEquals(1, result.outbound().getFirst().fares().size());
    assertEquals("BUSINESS", result.outbound().getFirst().fares().getFirst().cabinClass());
  }

  @Test
  void search_maxBudget_keepsFareWhenEffectivePriceWithinBudgetIncludingPromo() {
    FlightFareEntity fare = fare(1L, 1001L, "VN123", "AVAILABLE", "ECONOMY", "Vietnam");
    fare.setTotalPrice(new BigDecimal("3000000"));
    when(promotionBatchPreviewService.loadContext(any())).thenReturn(PromotionContext.empty());
    when(flightQueryService.findByRouteAndDate("SGN", "HAN", LocalDate.parse("2026-05-01")))
        .thenReturn(List.of(List.of(fare)));
    when(countryCurrencyResolver.resolveCountryCode(any())).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(flightPricingService.calculateTotalPrice(any(), anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(promotionBatchPreviewService.preview(any(), any(), anyString(), anyString(), anyBoolean()))
        .thenReturn(
            new PromoPreview(
                "APPLIED",
                "ok",
                new BigDecimal("3000000"),
                new BigDecimal("2000000"),
                new BigDecimal("1000000"),
                33));
    when(flightRankingService.buildFareRankKey(any(), any(), any(), any()))
        .thenReturn(
            recommendedKey(
                0, new BigDecimal("2000000"), fare.getFlight().getDepartureTime(), 1001L, 1L));
    when(flightPaginationService.paginate(any(), anyInt(), any()))
        .thenAnswer(
            inv ->
                new FlightPaginationService.PageResult<>(
                    inv.getArgument(0), null, false, inv.getArgument(1)));

    FlightSearchResult within =
        service.search(baseQueryBuilder().maxBudget(new BigDecimal("2500000")).build());
    assertEquals(1, within.outbound().size());
    assertEquals(true, within.outbound().getFirst().fares().getFirst().selectable());
    assertEquals(
        new BigDecimal("2000000"),
        within.outbound().getFirst().fares().getFirst().discountedTotalPrice());

    FlightSearchResult excluded =
        service.search(baseQueryBuilder().maxBudget(new BigDecimal("1500000")).build());
    assertEquals(1, excluded.outbound().size());
    assertEquals(1, excluded.outbound().getFirst().fares().size());
    assertEquals(false, excluded.outbound().getFirst().fares().getFirst().selectable());
  }

  @Test
  void search_departureTimeBucket_respectsMorningAndAfternoonBoundaries() {
    FlightFareEntity morning =
        fareAtLocalTimes(
            1L,
            100L,
            OffsetDateTime.of(2026, 5, 1, 11, 59, 0, 0, ZoneOffset.ofHours(7)),
            OffsetDateTime.of(2026, 5, 1, 14, 0, 0, 0, ZoneOffset.ofHours(7)));
    FlightFareEntity afternoon =
        fareAtLocalTimes(
            2L,
            101L,
            OffsetDateTime.of(2026, 5, 1, 12, 0, 0, 0, ZoneOffset.ofHours(7)),
            OffsetDateTime.of(2026, 5, 1, 15, 0, 0, 0, ZoneOffset.ofHours(7)));

    when(promotionBatchPreviewService.loadContext(any())).thenReturn(PromotionContext.empty());
    when(flightQueryService.findByRouteAndDate("SGN", "HAN", LocalDate.parse("2026-05-01")))
        .thenReturn(List.of(List.of(morning), List.of(afternoon)));
    when(countryCurrencyResolver.resolveCountryCode(any())).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(flightPricingService.calculateTotalPrice(any(), anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(promotionBatchPreviewService.preview(any(), any(), anyString(), anyString(), anyBoolean()))
        .thenReturn(PromoPreview.notApplicable("n", new BigDecimal("1000000")));
    when(flightRankingService.buildFareRankKey(any(), any(), any(), any()))
        .thenReturn(
            recommendedKey(
                2, new BigDecimal("1000000"), morning.getFlight().getDepartureTime(), 100L, 1L))
        .thenReturn(
            recommendedKey(
                2, new BigDecimal("1000000"), afternoon.getFlight().getDepartureTime(), 101L, 2L));
    when(flightPaginationService.paginate(any(), anyInt(), any()))
        .thenAnswer(
            inv ->
                new FlightPaginationService.PageResult<>(
                    inv.getArgument(0), null, false, inv.getArgument(1)));

    FlightSearchResult morningOnly =
        service.search(baseQueryBuilder().departureTimeBucket(TimeOfDayBucket.MORNING).build());
    assertEquals(1, morningOnly.outbound().size());
    assertEquals(100L, morningOnly.outbound().getFirst().flightId());

    FlightSearchResult afternoonOnly =
        service.search(baseQueryBuilder().departureTimeBucket(TimeOfDayBucket.AFTERNOON).build());
    assertEquals(1, afternoonOnly.outbound().size());
    assertEquals(101L, afternoonOnly.outbound().getFirst().flightId());
  }

  @Test
  void search_arrivalTimeBucket_filtersByArrivalLocalTime() {
    FlightFareEntity flight =
        fareAtLocalTimes(
            1L,
            200L,
            OffsetDateTime.of(2026, 5, 1, 10, 0, 0, 0, ZoneOffset.ofHours(7)),
            OffsetDateTime.of(2026, 5, 1, 18, 30, 0, 0, ZoneOffset.ofHours(7)));
    when(promotionBatchPreviewService.loadContext(any())).thenReturn(PromotionContext.empty());
    when(flightQueryService.findByRouteAndDate("SGN", "HAN", LocalDate.parse("2026-05-01")))
        .thenReturn(List.of(List.of(flight)));
    when(countryCurrencyResolver.resolveCountryCode(any())).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(flightPricingService.calculateTotalPrice(any(), anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(promotionBatchPreviewService.preview(any(), any(), anyString(), anyString(), anyBoolean()))
        .thenReturn(PromoPreview.notApplicable("n", new BigDecimal("1000000")));
    when(flightRankingService.buildFareRankKey(any(), any(), any(), any()))
        .thenReturn(
            recommendedKey(
                2, new BigDecimal("1000000"), flight.getFlight().getDepartureTime(), 200L, 1L));
    when(flightPaginationService.paginate(any(), anyInt(), any()))
        .thenAnswer(
            inv ->
                new FlightPaginationService.PageResult<>(
                    inv.getArgument(0), null, false, inv.getArgument(1)));

    FlightSearchResult eveningArrival =
        service.search(baseQueryBuilder().arrivalTimeBucket(TimeOfDayBucket.EVENING).build());
    assertEquals(1, eveningArrival.outbound().size());

    FlightSearchResult afternoonArrival =
        service.search(baseQueryBuilder().arrivalTimeBucket(TimeOfDayBucket.AFTERNOON).build());
    assertEquals(0, afternoonArrival.outbound().size());
  }

  @Test
  void search_combinedFilters_keepFlightsButPreferSelectableFaresForPaginationOrder() {
    FlightFareEntity cheap = fare(1L, 301L, "V1", "AVAILABLE", "ECONOMY", "Vietnam");
    cheap.setTotalPrice(new BigDecimal("1000000"));
    FlightFareEntity expensive = fare(2L, 302L, "V2", "AVAILABLE", "ECONOMY", "Vietnam");
    expensive.setTotalPrice(new BigDecimal("5000000"));
    FlightFareEntity mid = fare(3L, 303L, "V3", "AVAILABLE", "ECONOMY", "Vietnam");
    mid.setTotalPrice(new BigDecimal("1500000"));

    when(promotionBatchPreviewService.loadContext(any())).thenReturn(PromotionContext.empty());
    when(flightQueryService.findByRouteAndDate("SGN", "HAN", LocalDate.parse("2026-05-01")))
        .thenReturn(List.of(List.of(cheap), List.of(expensive), List.of(mid)));
    when(countryCurrencyResolver.resolveCountryCode(any())).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(flightPricingService.calculateTotalPrice(any(), anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(promotionBatchPreviewService.preview(any(), any(), anyString(), anyString(), anyBoolean()))
        .thenReturn(PromoPreview.notApplicable("n", new BigDecimal("1000000")));
    when(flightRankingService.buildFareRankKey(any(), any(), any(), any()))
        .thenAnswer(
            inv -> {
              FlightFareEntity f = inv.getArgument(1, FlightFareEntity.class);
              return recommendedKey(
                  2,
                  f.getTotalPrice(),
                  f.getFlight().getDepartureTime(),
                  f.getFlight().getId(),
                  f.getId());
            });

    FlightPaginationService realPagination = new FlightPaginationService();
    when(flightPaginationService.paginate(any(), anyInt(), any()))
        .thenAnswer(
            inv ->
                realPagination.paginate(
                    inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

    FlightSearchQuery query =
        new FlightSearchQuery(
            FlightSearchTripType.ONE_WAY,
            "SGN",
            "HAN",
            LocalDate.parse("2026-05-01"),
            null,
            1,
            0,
            0,
            null,
            null,
            null,
            null,
            1,
            Collections.emptySet(),
            new BigDecimal("2000000"),
            null,
            null,
            FlightConnectionFilter.ANY,
            FlightSortBy.RECOMMENDED);

    service.search(query);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<FlightSearchPageItem>> captor = ArgumentCaptor.forClass(List.class);
    verify(flightPaginationService).paginate(captor.capture(), eq(1), isNull());
    assertEquals(3, captor.getValue().size());
    assertTrue(
        captor.getValue().getFirst().rankKey().compareTo(captor.getValue().get(1).rankKey()) < 0);
  }

  @Test
  void search_hidesFlightWhenAllFaresAreSoldOutByInventory() {
    FlightFareEntity fare = fare(1L, 1001L, "VN123", "AVAILABLE", "ECONOMY", "Vietnam");
    when(promotionBatchPreviewService.loadContext(any())).thenReturn(PromotionContext.empty());
    when(flightQueryService.findByRouteAndDate("SGN", "HAN", LocalDate.parse("2026-05-01")))
        .thenReturn(List.of(List.of(fare)));
    when(countryCurrencyResolver.resolveCountryCode(any())).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(flightPricingService.calculateTotalPrice(any(), anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(flightInventoryAvailabilityService.hasEnoughSeats(1001L, 1L, 1)).thenReturn(false);
    when(promotionBatchPreviewService.preview(any(), any(), anyString(), anyString(), anyBoolean()))
        .thenReturn(PromoPreview.notApplicable("n", new BigDecimal("1100000")));
    when(flightPaginationService.paginate(any(), anyInt(), any()))
        .thenAnswer(
            inv ->
                new FlightPaginationService.PageResult<>(
                    inv.getArgument(0), null, false, inv.getArgument(1)));

    FlightSearchResult result = service.search(baseQueryBuilder().build());

    assertEquals(0, result.outbound().size());
  }

  @Test
  void search_keepsFlightWhenAtLeastOneFareHasInventory() {
    List<FlightFareEntity> group = economyAndBusinessOnSameFlight();
    when(promotionBatchPreviewService.loadContext(any())).thenReturn(PromotionContext.empty());
    when(flightQueryService.findByRouteAndDate("SGN", "HAN", LocalDate.parse("2026-05-01")))
        .thenReturn(List.of(group));
    when(countryCurrencyResolver.resolveCountryCode(any())).thenReturn("VN");
    when(countryCurrencyResolver.resolveCurrency("VN")).thenReturn("VND");
    when(flightPricingService.calculateTotalPrice(any(), anyInt(), anyInt(), anyInt()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(flightInventoryAvailabilityService.hasEnoughSeats(100L, 11L, 1))
        .thenReturn(false)
        .thenReturn(true);
    when(flightInventoryAvailabilityService.hasEnoughSeats(100L, 12L, 1))
        .thenReturn(true);
    when(promotionBatchPreviewService.preview(any(), any(), anyString(), anyString(), eq(false)))
        .thenReturn(PromoPreview.notApplicable("sold out", new BigDecimal("1000000")));
    when(promotionBatchPreviewService.preview(any(), any(), anyString(), anyString(), eq(true)))
        .thenReturn(PromoPreview.notApplicable("available", new BigDecimal("5000000")));
    OffsetDateTime dep = group.getFirst().getFlight().getDepartureTime();
    when(flightRankingService.buildFareRankKey(any(), any(), any(), any()))
        .thenReturn(recommendedKey(2, new BigDecimal("1000000"), dep, 100L, 1L))
        .thenReturn(recommendedKey(2, new BigDecimal("5000000"), dep, 100L, 2L));
    when(flightPaginationService.paginate(any(), anyInt(), any()))
        .thenAnswer(
            inv ->
                new FlightPaginationService.PageResult<>(
                    inv.getArgument(0), null, false, inv.getArgument(1)));

    FlightSearchResult result = service.search(baseQueryBuilder().build());

    assertEquals(1, result.outbound().size());
    assertEquals(2, result.outbound().getFirst().fares().size());
    assertEquals("SOLD_OUT", result.outbound().getFirst().fares().getFirst().fareStatus());
    assertEquals(false, result.outbound().getFirst().fares().getFirst().selectable());
    assertEquals(true, result.outbound().getFirst().fares().get(1).selectable());
  }

  private static FlightSearchQueryBuilder baseQueryBuilder() {
    return new FlightSearchQueryBuilder();
  }

  private static final class FlightSearchQueryBuilder {
    private Set<CabinClass> cabinClasses = Collections.emptySet();
    private BigDecimal maxBudget;
    private TimeOfDayBucket departureTimeBucket;
    private TimeOfDayBucket arrivalTimeBucket;
    private FlightSortBy sortBy = FlightSortBy.RECOMMENDED;

    FlightSearchQueryBuilder cabinClasses(Set<CabinClass> cabinClasses) {
      this.cabinClasses = cabinClasses;
      return this;
    }

    FlightSearchQueryBuilder maxBudget(BigDecimal maxBudget) {
      this.maxBudget = maxBudget;
      return this;
    }

    FlightSearchQueryBuilder departureTimeBucket(TimeOfDayBucket departureTimeBucket) {
      this.departureTimeBucket = departureTimeBucket;
      return this;
    }

    FlightSearchQueryBuilder arrivalTimeBucket(TimeOfDayBucket arrivalTimeBucket) {
      this.arrivalTimeBucket = arrivalTimeBucket;
      return this;
    }

    FlightSearchQueryBuilder sortBy(FlightSortBy sortBy) {
      this.sortBy = sortBy;
      return this;
    }

    FlightSearchQuery build() {
      return new FlightSearchQuery(
          FlightSearchTripType.ONE_WAY,
          "SGN",
          "HAN",
          LocalDate.parse("2026-05-01"),
          null,
          1,
          0,
          0,
          null,
          null,
          null,
          null,
          20,
          cabinClasses,
          maxBudget,
          departureTimeBucket,
          arrivalTimeBucket,
          FlightConnectionFilter.ANY,
          sortBy);
    }
  }

  private static FlightRankKey recommendedKey(
      int promoRank,
      BigDecimal effectivePrice,
      OffsetDateTime departure,
      long flightId,
      long fareId) {
    return new FlightRankKey(
        FlightSortBy.RECOMMENDED,
        BigDecimal.valueOf(promoRank),
        effectivePrice,
        null,
        departure,
        flightId,
        fareId);
  }

  private static List<FlightFareEntity> economyAndBusinessOnSameFlight() {
    OffsetDateTime dep = OffsetDateTime.parse("2026-05-01T02:00:00Z");
    OffsetDateTime arr = OffsetDateTime.parse("2026-05-01T04:00:00Z");
    AirlineEntity airline = new AirlineEntity();
    airline.setCode("VN");
    airline.setName("Vietnam Airlines");
    AirportEntity departure = new AirportEntity();
    departure.setCode("SGN");
    departure.setCountry("Vietnam");
    AirportEntity arrival = new AirportEntity();
    arrival.setCode("HAN");
    arrival.setCountry("Vietnam");
    FlightEntity flight = new FlightEntity();
    flight.setId(100L);
    flight.setAirline(airline);
    flight.setFlightNumber("VN1");
    flight.setFlightDate(LocalDate.parse("2026-05-01"));
    flight.setDepartureAirport(departure);
    flight.setArrivalAirport(arrival);
    flight.setDepartureTime(dep);
    flight.setArrivalTime(arr);
    flight.setStatus("SCHEDULED");

    FlightFareEntity eco = new FlightFareEntity();
    eco.setId(1L);
    eco.setFlight(flight);
    FareClassEntity fcEco = new FareClassEntity();
    fcEco.setId(11L);
    fcEco.setCode("Y");
    fcEco.setCabinClass("ECONOMY");
    eco.setFareClass(fcEco);
    eco.setTotalPrice(new BigDecimal("1000000"));
    eco.setStatus("AVAILABLE");
    eco.setUpdatedAt(OffsetDateTime.now());

    FlightFareEntity biz = new FlightFareEntity();
    biz.setId(2L);
    biz.setFlight(flight);
    FareClassEntity fcBiz = new FareClassEntity();
    fcBiz.setId(12L);
    fcBiz.setCode("J");
    fcBiz.setCabinClass("BUSINESS");
    biz.setFareClass(fcBiz);
    biz.setTotalPrice(new BigDecimal("5000000"));
    biz.setStatus("AVAILABLE");
    biz.setUpdatedAt(OffsetDateTime.now());

    return List.of(eco, biz);
  }

  private static FlightFareEntity fareAtLocalTimes(
      Long fareId, Long flightId, OffsetDateTime departure, OffsetDateTime arrival) {
    FlightFareEntity f = fare(fareId, flightId, "VNx", "AVAILABLE", "ECONOMY", "Vietnam");
    f.getFlight().setDepartureTime(departure);
    f.getFlight().setArrivalTime(arrival);
    f.setTotalPrice(new BigDecimal("1000000"));
    return f;
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
    fareClass.setId(fareId);
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

  private record TestRankedFlightOption(
      FlightSearchResult.FlightOptionView view, FlightRankKey rankKey)
      implements FlightSearchPageItem {}
}
