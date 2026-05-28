package com.example.appbootstrap.controller.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.application.booking.BookingRevalidateService;
import com.example.appbootstrap.application.flight.FlightSeatMapService;
import com.example.appbootstrap.application.flight.PromotionCatalogService;
import com.example.appbootstrap.controller.booking.dto.PassengerSummaryRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareResponse;
import com.example.appbootstrap.controller.booking.dto.RevalidateItineraryRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateItineraryResponse;
import com.example.appbootstrap.controller.flight.dto.FlightCalendarSearchQueryRequest;
import com.example.appbootstrap.controller.flight.dto.FlightCalendarSearchResponse;
import com.example.appbootstrap.controller.flight.dto.FlightSearchQueryRequest;
import com.example.appbootstrap.controller.flight.dto.FlightSearchResponse;
import com.example.appbootstrap.controller.flight.dto.FlightSeatMapResponse;
import com.example.common.exception.AppException;
import com.example.moduleflight.application.CabinClass;
import com.example.moduleflight.application.FlightCalendarSearchQuery;
import com.example.moduleflight.application.FlightCalendarSearchResult;
import com.example.moduleflight.application.FlightCalendarSearchService;
import com.example.moduleflight.application.FlightConnectionFilter;
import com.example.moduleflight.application.FlightSearchFacadeService;
import com.example.moduleflight.application.FlightSearchQuery;
import com.example.moduleflight.application.FlightSearchResult;
import com.example.moduleflight.application.FlightSortBy;
import com.example.moduleflight.application.TimeOfDayBucket;
import com.example.moduleflight.application.UserRecentAirportService;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.moduleidentity.entity.Role;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightsControllerTest {

  @Mock private FlightSearchFacadeService flightSearchFacadeService;
  @Mock private FlightCalendarSearchService flightCalendarSearchService;
  @Mock private BookingRevalidateService bookingRevalidateService;
  @Mock private FlightSeatMapService flightSeatMapService;
  @Mock private PromotionCatalogService promotionCatalogService;
  @Mock private UserRecentAirportService userRecentAirportService;

  private FlightsController controller;

  @BeforeEach
  void setUp() {
    controller =
        new FlightsController(
            flightSearchFacadeService,
            flightCalendarSearchService,
            bookingRevalidateService,
            flightSeatMapService,
            promotionCatalogService,
            userRecentAirportService);
  }

  @Test
  void searchFlights_mapsServiceResponse() {
    when(flightSearchFacadeService.search(any()))
        .thenReturn(
            new FlightSearchResult(
                List.of(
                    new FlightSearchResult.FlightOptionView(
                        11L,
                        "VN123",
                        "VN",
                        "Vietnam Airlines",
                        "SGN",
                        "HAN",
                        LocalDate.parse("2026-05-01"),
                        OffsetDateTime.parse("2026-05-01T02:00:00Z"),
                        OffsetDateTime.parse("2026-05-01T04:00:00Z"),
                        "VND",
                        List.of(
                            new FlightSearchResult.FareOptionView(
                                101L,
                                "ECONOMY",
                                "ECONOMY",
                                "AVAILABLE",
                                true,
                                new BigDecimal("1500000"),
                                new BigDecimal("1200000"),
                                new BigDecimal("300000"),
                                20,
                                "APPLIED",
                                "Promo applied successfully",
                                new FlightSearchResult.FareClassBenefitsView(
                                    1L,
                                    "Economy",
                                    "ECONOMY",
                                    20,
                                    7,
                                    new BigDecimal("300000"),
                                    new BigDecimal("700000"),
                                    false,
                                    true,
                                    false,
                                    false,
                                    new BigDecimal("1.00"),
                                    false,
                                    true,
                                    "INCLUDED",
                                    "STANDARD",
                                    false,
                                    false))))),
                List.of(),
                new FlightSearchResult.PageInfo("nextOutbound", true, 20),
                new FlightSearchResult.PageInfo(null, false, 20),
                new FlightSearchResult.PriceRange(
                    new BigDecimal("1200000"), new BigDecimal("1200000"), "VND"),
                null));

    FlightSearchResponse response =
        controller.searchFlights(
            null,
            new FlightSearchQueryRequest(
                "ONE_WAY",
                "SGN",
                "HAN",
                LocalDate.parse("2026-05-01"),
                null,
                1,
                0,
                0,
                "SAVE20",
                null,
                null,
                null,
                20,
                null,
                null,
                null,
                null,
                null,
                null));

    assertEquals(1, response.outbound().size());
    assertEquals("VN123", response.outbound().getFirst().flightNumber());
    assertEquals("APPLIED", response.outbound().getFirst().fares().getFirst().promoStatus());
    assertEquals(true, response.outbound().getFirst().fares().getFirst().selectable());
    assertEquals(true, response.outboundPageInfo().hasMore());
    assertEquals(new BigDecimal("1200000"), response.outboundPriceRange().minPrice());
    assertEquals(new BigDecimal("1200000"), response.outboundPriceRange().maxPrice());
    assertEquals("VND", response.outboundPriceRange().currency());
    assertNull(response.inboundPriceRange());
    assertEquals(
        "STANDARD",
        response.outbound().getFirst().fares().getFirst().fareClassBenefits().mealType());
    assertEquals(
        false,
        response.outbound().getFirst().fares().getFirst().fareClassBenefits().loungeAccess());
    assertEquals(
        true,
        response.outbound().getFirst().fares().getFirst().fareClassBenefits().seatSelection());
    assertEquals(
        "INCLUDED",
        response
            .outbound()
            .getFirst()
            .fares()
            .getFirst()
            .fareClassBenefits()
            .seatSelectionPolicy());
    assertEquals(
        false,
        response.outbound().getFirst().fares().getFirst().fareClassBenefits().wifiIncluded());
    assertEquals(
        false,
        response.outbound().getFirst().fares().getFirst().fareClassBenefits().fastTrackSecurity());
    verify(userRecentAirportService, never()).recordSearch(any(), any(), any());
  }

  @Test
  void revalidateFare_delegatesToBookingRevalidateService() {
    RevalidateFareRequest request =
        new RevalidateFareRequest(11L, 101L, "SAVE20", new PassengerSummaryRequest(1, 0, 0));
    RevalidateFareResponse expected =
        new RevalidateFareResponse(
            "RV_ABC123",
            900,
            11L,
            101L,
            "SAVE20",
            new RevalidateFareResponse.PassengerSummaryResponse(1, 0, 0),
            new RevalidateFareResponse.PricingResponse(
                "VND",
                new BigDecimal("1200000"),
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                new BigDecimal("1500000"),
                BigDecimal.ZERO,
                null,
                new BigDecimal("1500000")));
    when(bookingRevalidateService.revalidate(request)).thenReturn(expected);

    RevalidateFareResponse response = controller.revalidateFare(request);

    assertEquals(expected, response);
    verify(bookingRevalidateService).revalidate(request);
  }

  @Test
  void revalidateItinerary_delegatesToBookingRevalidateService() {
    RevalidateItineraryRequest request =
        new RevalidateItineraryRequest(
            "ROUND_TRIP",
            "SAVE20",
            new PassengerSummaryRequest(1, 0, 0),
            List.of(
                new RevalidateItineraryRequest.SegmentRequest("OUTBOUND", 11L, 101L),
                new RevalidateItineraryRequest.SegmentRequest("INBOUND", 12L, 202L)));
    RevalidateItineraryResponse expected =
        new RevalidateItineraryResponse(
            "RV_RT_ABC123",
            900,
            "ROUND_TRIP",
            "SAVE20",
            new RevalidateItineraryResponse.PassengerSummaryResponse(1, 0, 0),
            new RevalidateItineraryResponse.PricingResponse(
                "VND",
                new BigDecimal("2300000"),
                new BigDecimal("700000"),
                BigDecimal.ZERO,
                new BigDecimal("3000000"),
                new BigDecimal("300000"),
                10,
                new BigDecimal("2700000")),
            List.of(
                new RevalidateItineraryResponse.SegmentResponse(
                    "OUTBOUND",
                    11L,
                    101L,
                    5L,
                    "ECO",
                    "Economy",
                    "ECONOMY",
                    "VND",
                    new RevalidateItineraryResponse.PricingResponse(
                        "VND",
                        new BigDecimal("1100000"),
                        new BigDecimal("400000"),
                        BigDecimal.ZERO,
                        new BigDecimal("1500000"),
                        BigDecimal.ZERO,
                        null,
                        new BigDecimal("1500000"))),
                new RevalidateItineraryResponse.SegmentResponse(
                    "INBOUND",
                    12L,
                    202L,
                    6L,
                    "BUS",
                    "Business",
                    "BUSINESS",
                    "VND",
                    new RevalidateItineraryResponse.PricingResponse(
                        "VND",
                        new BigDecimal("1200000"),
                        new BigDecimal("300000"),
                        BigDecimal.ZERO,
                        new BigDecimal("1500000"),
                        BigDecimal.ZERO,
                        null,
                        new BigDecimal("1500000")))));
    when(bookingRevalidateService.revalidateItinerary(request)).thenReturn(expected);

    RevalidateItineraryResponse response = controller.revalidateItinerary(request);

    assertEquals(expected, response);
    verify(bookingRevalidateService).revalidateItinerary(request);
  }

  @Test
  void getSeatMap_mapsServiceResponse() {
    when(flightSeatMapService.getSeatMap(11L, 101L))
        .thenReturn(
            new FlightSeatMapService.FlightSeatMapView(
                11L,
                101L,
                1L,
                "ECONOMY",
                "PAID",
                null,
                "VND",
                new FlightSeatMapService.AircraftLayoutView(
                    List.of("A", "B", "C", "D", "E", "F"),
                    List.of("C"),
                    List.of(
                        new FlightSeatMapService.SeatClassZoneView("BUSINESS", 1, 2),
                        new FlightSeatMapService.SeatClassZoneView("ECONOMY", 3, 30))),
                new FlightSeatMapService.LegendView(
                    List.of("AVAILABLE", "RESERVED", "BOOKED", "UNAVAILABLE"),
                    List.of("WINDOW", "MIDDLE", "AISLE"),
                    List.of("STANDARD", "PREFERRED", "EXIT_ROW", "EXTRA_LEGROOM")),
                List.of(
                    new FlightSeatMapService.RowView(
                        1,
                        "BUSINESS",
                        List.of(
                            new FlightSeatMapService.SeatView(
                                1,
                                "A",
                                502L,
                                "1A",
                                "BUSINESS",
                                "WINDOW",
                                "STANDARD",
                                "AVAILABLE",
                                false,
                                false,
                                null,
                                null,
                                "SEAT_NOT_ALLOWED_FOR_FARE"))),
                    new FlightSeatMapService.RowView(
                        12,
                        "ECONOMY",
                        List.of(
                            new FlightSeatMapService.SeatView(
                                12,
                                "A",
                                501L,
                                "12A",
                                "ECONOMY",
                                "WINDOW",
                                "STANDARD",
                                "AVAILABLE",
                                true,
                                false,
                                new BigDecimal("20000"),
                                "VND",
                                null))))));

    FlightSeatMapResponse response = controller.getSeatMap(11L, 101L);

    assertEquals("PAID", response.seatSelectionPolicy());
    assertEquals("Seat selection with surcharge", response.policyDisplay().label());
    assertEquals(null, response.includedSeatCategoryCode());
    assertEquals(List.of("A", "B", "C", "D", "E", "F"), response.layout().columns());
    assertEquals(List.of("C"), response.layout().aisleAfterColumns());
    assertEquals(2, response.rows().size());
    assertEquals("STANDARD", response.rows().get(1).cells().getFirst().seatCategory());
    assertEquals("Window", response.rows().get(1).cells().getFirst().seatTypeDisplay().label());
    assertEquals(
        "Standard", response.rows().get(1).cells().getFirst().seatCategoryDisplay().label());
    assertEquals("Available", response.rows().get(1).cells().getFirst().statusDisplay().label());
    assertEquals(new BigDecimal("20000"), response.rows().get(1).cells().getFirst().extraPrice());
    assertEquals("+20000 VND", response.rows().get(1).cells().getFirst().displayPrice().text());
    assertEquals("PAID", response.rows().get(1).cells().getFirst().pricingBadge().code());
    assertEquals(
        "SEAT_NOT_ALLOWED_FOR_FARE", response.rows().getFirst().cells().getFirst().reason());
    assertEquals(
        "NOT_ALLOWED", response.rows().getFirst().cells().getFirst().pricingBadge().code());
    assertEquals(
        "Not allowed", response.rows().getFirst().cells().getFirst().displayPrice().text());
    assertEquals(4, response.legend().pricingItems().size());
    assertEquals("Window", response.legend().seatTypeItems().getFirst().label());
    verify(flightSeatMapService).getSeatMap(11L, 101L);
  }

  @Test
  void searchCalendar_mapsServiceResponse() {
    when(flightCalendarSearchService.search(any()))
        .thenReturn(
            new FlightCalendarSearchResult(
                LocalDate.parse("2026-05-26"),
                LocalDate.parse("2026-05-26"),
                "VND",
                List.of(
                    new FlightCalendarSearchResult.CalendarDayResult(
                        LocalDate.parse("2026-05-25"), false, null, null, null, 0, List.of()),
                    new FlightCalendarSearchResult.CalendarDayResult(
                        LocalDate.parse("2026-05-26"),
                        true,
                        new BigDecimal("1200000"),
                        new BigDecimal("1500000"),
                        "VND",
                        1,
                        List.of(
                            new FlightSearchResult.FlightOptionView(
                                11L,
                                "VN123",
                                "VN",
                                "Vietnam Airlines",
                                "SGN",
                                "HAN",
                                LocalDate.parse("2026-05-26"),
                                OffsetDateTime.parse("2026-05-26T02:00:00Z"),
                                OffsetDateTime.parse("2026-05-26T04:00:00Z"),
                                "VND",
                                List.of())))),
                new FlightCalendarSearchResult.CalendarDayResult(
                    LocalDate.parse("2026-05-26"),
                    true,
                    new BigDecimal("1200000"),
                    new BigDecimal("1500000"),
                    "VND",
                    1,
                    List.of(
                        new FlightSearchResult.FlightOptionView(
                            11L,
                            "VN123",
                            "VN",
                            "Vietnam Airlines",
                            "SGN",
                            "HAN",
                            LocalDate.parse("2026-05-26"),
                            OffsetDateTime.parse("2026-05-26T02:00:00Z"),
                            OffsetDateTime.parse("2026-05-26T04:00:00Z"),
                            "VND",
                            List.of())))));

    FlightCalendarSearchResponse response =
        controller.searchCalendar(
            new FlightCalendarSearchQueryRequest(
                "ONE_WAY",
                "SGN",
                "HAN",
                LocalDate.parse("2026-05-26"),
                1,
                0,
                0,
                null,
                10,
                null,
                null,
                null,
                null,
                null,
                null,
                3,
                3));

    assertEquals("VND", response.currency());
    assertEquals(2, response.days().size());
    assertEquals(LocalDate.parse("2026-05-26"), response.selectedDay().date());
    assertEquals("VN123", response.selectedDay().flights().getFirst().flightNumber());
  }

  @Test
  void searchCalendar_mapsOptionalFiltersToCalendarQuery() {
    when(flightCalendarSearchService.search(any()))
        .thenReturn(
            new FlightCalendarSearchResult(
                LocalDate.parse("2026-05-26"),
                LocalDate.parse("2026-05-26"),
                null,
                List.of(),
                null));

    controller.searchCalendar(
        new FlightCalendarSearchQueryRequest(
            "ONE_WAY",
            "SGN",
            "HAN",
            LocalDate.parse("2026-05-26"),
            1,
            0,
            0,
            "SAVE20",
            12,
            List.of("ECONOMY", "BUSINESS"),
            new BigDecimal("2000000"),
            "MORNING",
            "EVENING",
            "DIRECT_ONLY",
            "CHEAPEST",
            2,
            4));

    ArgumentCaptor<FlightCalendarSearchQuery> captor =
        ArgumentCaptor.forClass(FlightCalendarSearchQuery.class);
    verify(flightCalendarSearchService).search(captor.capture());
    FlightCalendarSearchQuery q = captor.getValue();
    assertEquals(LocalDate.parse("2026-05-26"), q.centerDate());
    assertEquals(12, q.limit());
    assertEquals(2, q.daysBefore());
    assertEquals(4, q.daysAfter());
    assertEquals(Set.of(CabinClass.ECONOMY, CabinClass.BUSINESS), q.cabinClasses());
    assertEquals(new BigDecimal("2000000"), q.maxBudget());
    assertEquals(TimeOfDayBucket.MORNING, q.departureTimeBucket());
    assertEquals(TimeOfDayBucket.EVENING, q.arrivalTimeBucket());
    assertEquals(FlightConnectionFilter.DIRECT_ONLY, q.connectionFilter());
    assertEquals(FlightSortBy.CHEAPEST, q.sortBy());
  }

  @Test
  void searchCalendar_defaultsWindowAndLimit() {
    when(flightCalendarSearchService.search(any()))
        .thenReturn(
            new FlightCalendarSearchResult(
                LocalDate.parse("2026-05-26"),
                LocalDate.parse("2026-05-26"),
                null,
                List.of(),
                null));

    controller.searchCalendar(
        new FlightCalendarSearchQueryRequest(
            "ONE_WAY",
            "SGN",
            "HAN",
            LocalDate.parse("2026-05-26"),
            1,
            0,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null));

    ArgumentCaptor<FlightCalendarSearchQuery> captor =
        ArgumentCaptor.forClass(FlightCalendarSearchQuery.class);
    verify(flightCalendarSearchService).search(captor.capture());
    assertEquals(10, captor.getValue().limit());
    assertEquals(3, captor.getValue().daysBefore());
    assertEquals(3, captor.getValue().daysAfter());
  }

  @Test
  void searchFlights_mapsOptionalFiltersToFlightSearchQuery() {
    when(flightSearchFacadeService.search(any()))
        .thenReturn(
            new FlightSearchResult(
                List.of(),
                List.of(),
                new FlightSearchResult.PageInfo(null, false, 20),
                new FlightSearchResult.PageInfo(null, false, 20),
                null,
                null));

    controller.searchFlights(
        null,
        new FlightSearchQueryRequest(
            "ONE_WAY",
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
            List.of("ECONOMY", "BUSINESS"),
            new BigDecimal("2000000"),
            "MORNING",
            "EVENING",
            "DIRECT_ONLY",
            "CHEAPEST"));

    ArgumentCaptor<FlightSearchQuery> captor = ArgumentCaptor.forClass(FlightSearchQuery.class);
    verify(flightSearchFacadeService).search(captor.capture());
    FlightSearchQuery q = captor.getValue();
    assertEquals(Set.of(CabinClass.ECONOMY, CabinClass.BUSINESS), q.cabinClasses());
    assertEquals(new BigDecimal("2000000"), q.maxBudget());
    assertEquals(TimeOfDayBucket.MORNING, q.departureTimeBucket());
    assertEquals(TimeOfDayBucket.EVENING, q.arrivalTimeBucket());
    assertEquals(FlightConnectionFilter.DIRECT_ONLY, q.connectionFilter());
    assertEquals(FlightSortBy.CHEAPEST, q.sortBy());
  }

  @Test
  void searchFlights_defaultsSortByToRecommended() {
    when(flightSearchFacadeService.search(any()))
        .thenReturn(
            new FlightSearchResult(
                List.of(),
                List.of(),
                new FlightSearchResult.PageInfo(null, false, 20),
                new FlightSearchResult.PageInfo(null, false, 20),
                null,
                null));

    controller.searchFlights(
        null,
        new FlightSearchQueryRequest(
            "ONE_WAY",
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
            null,
            null,
            null,
            null,
            null,
            null));

    ArgumentCaptor<FlightSearchQuery> captor = ArgumentCaptor.forClass(FlightSearchQuery.class);
    verify(flightSearchFacadeService).search(captor.capture());
    assertEquals(FlightSortBy.RECOMMENDED, captor.getValue().sortBy());
  }

  @Test
  void searchFlights_recordsRecentAirportsForAuthenticatedUser() {
    when(flightSearchFacadeService.search(any()))
        .thenReturn(
            new FlightSearchResult(
                List.of(),
                List.of(),
                new FlightSearchResult.PageInfo(null, false, 20),
                new FlightSearchResult.PageInfo(null, false, 20),
                null,
                null));

    controller.searchFlights(
        new AppUserPrincipal(42L, "user@example.com", "secret", Role.USER, null),
        new FlightSearchQueryRequest(
            "ONE_WAY",
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
            null,
            null,
            null,
            null,
            null,
            null));

    verify(userRecentAirportService).recordSearch(42L, "SGN", "HAN");
  }

  @Test
  void searchFlights_rejectsInvalidCabinClass() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                controller.searchFlights(
                    null,
                    new FlightSearchQueryRequest(
                        "ONE_WAY",
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
                        List.of("FIRST_CLASS"),
                        null,
                        null,
                        null,
                        null,
                        null)));

    assertEquals("INVALID_CABIN_CLASS", exception.getCode());
  }

  @Test
  void searchFlights_rejectsInvalidTimeBucket() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                controller.searchFlights(
                    null,
                    new FlightSearchQueryRequest(
                        "ONE_WAY",
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
                        null,
                        null,
                        "NIGHT",
                        null,
                        null,
                        null)));

    assertEquals("INVALID_TIME_BUCKET", exception.getCode());
  }

  @Test
  void searchFlights_rejectsInvalidConnectionFilter() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                controller.searchFlights(
                    null,
                    new FlightSearchQueryRequest(
                        "ONE_WAY",
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
                        null,
                        null,
                        null,
                        null,
                        "NONSTOP",
                        null)));

    assertEquals("INVALID_CONNECTION_FILTER", exception.getCode());
  }

  @Test
  void searchFlights_rejectsInvalidSortBy() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                controller.searchFlights(
                    null,
                    new FlightSearchQueryRequest(
                        "ONE_WAY",
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
                        null,
                        null,
                        null,
                        null,
                        null,
                        "BEST")));

    assertEquals("INVALID_SORT_BY", exception.getCode());
  }

  @Test
  void searchCalendar_rejectsRoundTrip() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                controller.searchCalendar(
                    new FlightCalendarSearchQueryRequest(
                        "ROUND_TRIP",
                        "SGN",
                        "HAN",
                        LocalDate.parse("2026-05-26"),
                        1,
                        0,
                        0,
                        null,
                        10,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        3,
                        3)));

    assertEquals("INVALID_TRIP_TYPE", exception.getCode());
  }

  @Test
  void searchCalendar_rejectsTooWideWindow() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                controller.searchCalendar(
                    new FlightCalendarSearchQueryRequest(
                        "ONE_WAY",
                        "SGN",
                        "HAN",
                        LocalDate.parse("2026-05-26"),
                        1,
                        0,
                        0,
                        null,
                        10,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        8,
                        7)));

    assertEquals("INVALID_CALENDAR_WINDOW", exception.getCode());
  }

  @Test
  void maxBudget_zeroFailsBeanValidation() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      FlightSearchQueryRequest request =
          new FlightSearchQueryRequest(
              "ONE_WAY",
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
              null,
              BigDecimal.ZERO,
              null,
              null,
              null,
              null);
      assertFalse(validator.validate(request).isEmpty());
    }
  }

  @Test
  void searchCalendar_maxBudgetZeroFailsBeanValidation() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();
      FlightCalendarSearchQueryRequest request =
          new FlightCalendarSearchQueryRequest(
              "ONE_WAY",
              "SGN",
              "HAN",
              LocalDate.parse("2026-05-26"),
              1,
              0,
              0,
              null,
              10,
              null,
              BigDecimal.ZERO,
              null,
              null,
              null,
              null,
              null,
              null);
      assertFalse(validator.validate(request).isEmpty());
    }
  }

  @Test
  void searchFlights_sameRouteReturnsEmptyResult() {
    when(flightSearchFacadeService.search(any()))
        .thenReturn(
            new FlightSearchResult(
                List.of(),
                List.of(),
                new FlightSearchResult.PageInfo(null, false, 20),
                new FlightSearchResult.PageInfo(null, false, 20),
                null,
                null));

    FlightSearchResponse response =
        controller.searchFlights(
            null,
            new FlightSearchQueryRequest(
                "ONE_WAY",
                "SGN",
                "SGN",
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
                null,
                null,
                null,
                null,
                null,
                null));

    assertEquals(List.of(), response.outbound());
    assertEquals(List.of(), response.inbound());
  }

  @Test
  void searchFlights_rejectsInfantGreaterThanAdult() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                controller.searchFlights(
                    null,
                    new FlightSearchQueryRequest(
                        "ONE_WAY",
                        "SGN",
                        "HAN",
                        LocalDate.parse("2026-05-01"),
                        null,
                        1,
                        0,
                        2,
                        null,
                        null,
                        null,
                        null,
                        20,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));

    assertEquals("INVALID_PASSENGER_BREAKDOWN", exception.getCode());
  }

  @Test
  void searchCalendar_rejectsInfantGreaterThanAdult() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                controller.searchCalendar(
                    new FlightCalendarSearchQueryRequest(
                        "ONE_WAY",
                        "SGN",
                        "HAN",
                        LocalDate.parse("2026-05-26"),
                        1,
                        0,
                        2,
                        null,
                        10,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        3,
                        3)));

    assertEquals("INVALID_PASSENGER_BREAKDOWN", exception.getCode());
  }
}
