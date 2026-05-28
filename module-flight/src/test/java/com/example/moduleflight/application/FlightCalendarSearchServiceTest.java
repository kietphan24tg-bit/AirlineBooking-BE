package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class FlightCalendarSearchServiceTest {

  @Mock private FlightSearchFacadeService flightSearchFacadeService;

  private FlightCalendarSearchService service;

  @BeforeEach
  void setUp() {
    service = new FlightCalendarSearchService(flightSearchFacadeService);
  }

  @Test
  void search_buildsCalendarWindowAndReusesDailySearchResults() {
    when(flightSearchFacadeService.search(any()))
        .thenReturn(emptyResult())
        .thenReturn(
            resultFor("2026-05-24", "VN124", new BigDecimal("980000"), new BigDecimal("1200000")))
        .thenReturn(
            resultFor("2026-05-25", "VN125", new BigDecimal("990000"), new BigDecimal("1250000")))
        .thenReturn(
            resultFor("2026-05-26", "VN126", new BigDecimal("1000000"), new BigDecimal("1300000")))
        .thenReturn(
            resultFor("2026-05-27", "VN127", new BigDecimal("1010000"), new BigDecimal("1310000")))
        .thenReturn(
            resultFor("2026-05-28", "VN128", new BigDecimal("1020000"), new BigDecimal("1320000")))
        .thenReturn(emptyResult());

    FlightCalendarSearchResult result =
        service.search(
            new FlightCalendarSearchQuery(
                FlightSearchTripType.ONE_WAY,
                "SGN",
                "HAN",
                LocalDate.parse("2026-05-26"),
                2,
                1,
                0,
                "SAVE20",
                10,
                Set.of(CabinClass.ECONOMY),
                new BigDecimal("2000000"),
                TimeOfDayBucket.MORNING,
                TimeOfDayBucket.EVENING,
                FlightConnectionFilter.DIRECT_ONLY,
                FlightSortBy.CHEAPEST,
                3,
                3));

    assertEquals(LocalDate.parse("2026-05-26"), result.centerDate());
    assertEquals(LocalDate.parse("2026-05-26"), result.selectedDate());
    assertEquals("VND", result.currency());
    assertEquals(7, result.days().size());
    assertFalse(result.days().getFirst().available());
    assertNull(result.days().getFirst().lowestPrice());
    assertEquals(LocalDate.parse("2026-05-23"), result.days().getFirst().date());
    assertEquals(LocalDate.parse("2026-05-29"), result.days().getLast().date());
    assertEquals(new BigDecimal("1000000"), result.selectedDay().lowestPrice());
    assertEquals("VN126", result.selectedDay().flights().getFirst().flightNumber());
    assertEquals(1, result.selectedDay().flightCount());

    ArgumentCaptor<FlightSearchQuery> captor = ArgumentCaptor.forClass(FlightSearchQuery.class);
    verify(flightSearchFacadeService, times(7)).search(captor.capture());
    List<FlightSearchQuery> queries = captor.getAllValues();
    assertEquals(LocalDate.parse("2026-05-23"), queries.getFirst().departureDate());
    assertEquals(LocalDate.parse("2026-05-29"), queries.getLast().departureDate());
    assertEquals(Set.of(CabinClass.ECONOMY), queries.get(3).cabinClasses());
    assertEquals(new BigDecimal("2000000"), queries.get(3).maxBudget());
    assertEquals(TimeOfDayBucket.MORNING, queries.get(3).departureTimeBucket());
    assertEquals(TimeOfDayBucket.EVENING, queries.get(3).arrivalTimeBucket());
    assertEquals(FlightConnectionFilter.DIRECT_ONLY, queries.get(3).connectionFilter());
    assertEquals(FlightSortBy.CHEAPEST, queries.get(3).sortBy());
    assertEquals(10, queries.get(3).limit());
  }

  @Test
  void search_keepsEmptyDaysUnavailable() {
    when(flightSearchFacadeService.search(any())).thenReturn(emptyResult());

    FlightCalendarSearchResult result =
        service.search(
            new FlightCalendarSearchQuery(
                FlightSearchTripType.ONE_WAY,
                "SGN",
                "HAN",
                LocalDate.parse("2026-05-26"),
                1,
                0,
                0,
                null,
                10,
                Set.of(),
                null,
                null,
                null,
                FlightConnectionFilter.ANY,
                FlightSortBy.RECOMMENDED,
                1,
                1));

    assertEquals(3, result.days().size());
    assertNull(result.currency());
    assertFalse(result.selectedDay().available());
    assertEquals(0, result.selectedDay().flightCount());
    assertEquals(List.of(), result.selectedDay().flights());
  }

  private FlightSearchResult emptyResult() {
    return new FlightSearchResult(
        List.of(),
        List.of(),
        new FlightSearchResult.PageInfo(null, false, 10),
        new FlightSearchResult.PageInfo(null, false, 10),
        null,
        null);
  }

  private FlightSearchResult resultFor(
      String date, String flightNumber, BigDecimal minPrice, BigDecimal maxPrice) {
    return new FlightSearchResult(
        List.of(
            new FlightSearchResult.FlightOptionView(
                11L,
                flightNumber,
                "VN",
                "Vietnam Airlines",
                "SGN",
                "HAN",
                LocalDate.parse(date),
                OffsetDateTime.parse(date + "T02:00:00Z"),
                OffsetDateTime.parse(date + "T04:00:00Z"),
                "VND",
                List.of())),
        List.of(),
        new FlightSearchResult.PageInfo(null, false, 10),
        new FlightSearchResult.PageInfo(null, false, 10),
        new FlightSearchResult.PriceRange(minPrice, maxPrice, "VND"),
        null);
  }
}
