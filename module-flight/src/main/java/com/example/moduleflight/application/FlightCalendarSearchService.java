package com.example.moduleflight.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlightCalendarSearchService {

  private final FlightSearchFacadeService flightSearchFacadeService;

  public FlightCalendarSearchService(FlightSearchFacadeService flightSearchFacadeService) {
    this.flightSearchFacadeService = flightSearchFacadeService;
  }

  @Transactional(readOnly = true)
  public FlightCalendarSearchResult search(FlightCalendarSearchQuery query) {
    List<FlightCalendarSearchResult.CalendarDayResult> days = new ArrayList<>();

    // Intentionally sequential in v1 so every day reuses the exact same search semantics.
    for (int offset = -query.daysBefore(); offset <= query.daysAfter(); offset++) {
      var date = query.centerDate().plusDays(offset);
      var daySearchResult =
          flightSearchFacadeService.search(
              new FlightSearchQuery(
                  query.tripType(),
                  query.fromAirportCode(),
                  query.toAirportCode(),
                  date,
                  null,
                  query.adult(),
                  query.child(),
                  query.infant(),
                  query.promoCode(),
                  null,
                  null,
                  null,
                  query.limit(),
                  query.cabinClasses(),
                  query.maxBudget(),
                  query.departureTimeBucket(),
                  query.arrivalTimeBucket(),
                  query.connectionFilter(),
                  query.sortBy()));

      var priceRange = daySearchResult.outboundPriceRange();
      var flights = daySearchResult.outbound();
      String currency =
          priceRange != null
              ? priceRange.currency()
              : flights.isEmpty() ? null : flights.getFirst().currency();

      days.add(
          new FlightCalendarSearchResult.CalendarDayResult(
              date,
              !flights.isEmpty(),
              priceRange != null ? priceRange.minPrice() : null,
              priceRange != null ? priceRange.maxPrice() : null,
              currency,
              flights.size(),
              flights));
    }

    var selectedDate = query.centerDate();
    var selectedDay =
        days.stream().filter(day -> day.date().equals(selectedDate)).findFirst().orElse(null);
    String currency =
        days.stream()
            .map(FlightCalendarSearchResult.CalendarDayResult::currency)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);

    return new FlightCalendarSearchResult(
        query.centerDate(), selectedDate, currency, days, selectedDay);
  }
}
