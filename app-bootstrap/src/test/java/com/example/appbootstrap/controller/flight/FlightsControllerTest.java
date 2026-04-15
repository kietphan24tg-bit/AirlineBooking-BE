package com.example.appbootstrap.controller.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.controller.flight.dto.FlightSearchQueryRequest;
import com.example.appbootstrap.controller.flight.dto.FlightSearchResponse;
import com.example.common.exception.AppException;
import com.example.moduleflight.application.FlightSearchFacadeService;
import com.example.moduleflight.application.FlightSearchResult;
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
class FlightsControllerTest {

  @Mock private FlightSearchFacadeService flightSearchFacadeService;

  private FlightsController controller;

  @BeforeEach
  void setUp() {
    controller = new FlightsController(flightSearchFacadeService);
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
                new FlightSearchResult.PageInfo(null, false, 20)));

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
                20));

    assertEquals(1, response.outbound().size());
    assertEquals("VN123", response.outbound().getFirst().flightNumber());
    assertEquals("APPLIED", response.outbound().getFirst().fares().getFirst().promoStatus());
    assertEquals(true, response.outboundPageInfo().hasMore());
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
                        20)));

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
                        20)));

    assertEquals("INVALID_PASSENGER_BREAKDOWN", exception.getCode());
  }
}
