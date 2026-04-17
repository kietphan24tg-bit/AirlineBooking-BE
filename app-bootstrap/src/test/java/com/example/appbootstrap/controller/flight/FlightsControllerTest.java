package com.example.appbootstrap.controller.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.application.booking.BookingRevalidateService;
import com.example.appbootstrap.controller.booking.dto.PassengerSummaryRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareResponse;
import com.example.appbootstrap.controller.flight.dto.FlightSearchQueryRequest;
import com.example.appbootstrap.controller.flight.dto.FlightSearchResponse;
import com.example.common.exception.AppException;
import com.example.moduleflight.application.CabinClass;
import com.example.moduleflight.application.FlightConnectionFilter;
import com.example.moduleflight.application.FlightSearchFacadeService;
import com.example.moduleflight.application.FlightSearchQuery;
import com.example.moduleflight.application.FlightSearchResult;
import com.example.moduleflight.application.TimeOfDayBucket;
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
  @Mock private BookingRevalidateService bookingRevalidateService;

  private FlightsController controller;

  @BeforeEach
  void setUp() {
    controller = new FlightsController(flightSearchFacadeService, bookingRevalidateService);
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
                                new BigDecimal("1500000"),
                                new BigDecimal("1200000"),
                                new BigDecimal("300000"),
                                20,
                                "APPLIED",
                                "Promo applied successfully")))),
                List.of(),
                new FlightSearchResult.PageInfo("nextOutbound", true, 20),
                new FlightSearchResult.PageInfo(null, false, 20),
                new FlightSearchResult.PriceRange(
                    new BigDecimal("1200000"), new BigDecimal("1200000"), "VND"),
                null));

    FlightSearchResponse response =
        controller.searchFlights(
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
                null));

    assertEquals(1, response.outbound().size());
    assertEquals("VN123", response.outbound().getFirst().flightNumber());
    assertEquals("APPLIED", response.outbound().getFirst().fares().getFirst().promoStatus());
    assertEquals(true, response.outboundPageInfo().hasMore());
    assertEquals(new BigDecimal("1200000"), response.outboundPriceRange().minPrice());
    assertEquals(new BigDecimal("1200000"), response.outboundPriceRange().maxPrice());
    assertEquals("VND", response.outboundPriceRange().currency());
    assertNull(response.inboundPriceRange());
  }

  @Test
  void revalidateFare_delegatesToBookingRevalidateService() {
    RevalidateFareRequest request =
        new RevalidateFareRequest(
            11L, 101L, new BigDecimal("1500000"), "SAVE20", new PassengerSummaryRequest(1, 0, 0));
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
            "DIRECT_ONLY"));

    ArgumentCaptor<FlightSearchQuery> captor = ArgumentCaptor.forClass(FlightSearchQuery.class);
    verify(flightSearchFacadeService).search(captor.capture());
    FlightSearchQuery q = captor.getValue();
    assertEquals(Set.of(CabinClass.ECONOMY, CabinClass.BUSINESS), q.cabinClasses());
    assertEquals(new BigDecimal("2000000"), q.maxBudget());
    assertEquals(TimeOfDayBucket.MORNING, q.departureTimeBucket());
    assertEquals(TimeOfDayBucket.EVENING, q.arrivalTimeBucket());
    assertEquals(FlightConnectionFilter.DIRECT_ONLY, q.connectionFilter());
  }

  @Test
  void searchFlights_rejectsInvalidCabinClass() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                controller.searchFlights(
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
                        "NONSTOP")));

    assertEquals("INVALID_CONNECTION_FILTER", exception.getCode());
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
              null);
      assertFalse(validator.validate(request).isEmpty());
    }
  }

  @Test
  void searchFlights_rejectsSameRoute() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                controller.searchFlights(
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
                        null)));

    assertEquals("INVALID_ROUTE", exception.getCode());
  }

  @Test
  void searchFlights_rejectsInfantGreaterThanAdult() {
    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                controller.searchFlights(
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
                        null)));

    assertEquals("INVALID_PASSENGER_BREAKDOWN", exception.getCode());
  }
}
