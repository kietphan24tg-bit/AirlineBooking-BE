package com.example.moduleweb.controller.flight.dto;

import com.example.moduleflight.application.FlightCalendarSearchResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FlightCalendarSearchResponse(
    LocalDate centerDate,
    LocalDate selectedDate,
    String currency,
    List<CalendarDayResponse> days,
    CalendarDayResponse selectedDay) {

  public static FlightCalendarSearchResponse from(FlightCalendarSearchResult result) {
    return new FlightCalendarSearchResponse(
        result.centerDate(),
        result.selectedDate(),
        result.currency(),
        result.days().stream().map(CalendarDayResponse::from).toList(),
        result.selectedDay() == null ? null : CalendarDayResponse.from(result.selectedDay()));
  }

  public record CalendarDayResponse(
      LocalDate date,
      boolean available,
      BigDecimal lowestPrice,
      BigDecimal highestPrice,
      String currency,
      int flightCount,
      List<FlightSearchResponse.FlightSearchItemResponse> flights) {
    static CalendarDayResponse from(FlightCalendarSearchResult.CalendarDayResult result) {
      return new CalendarDayResponse(
          result.date(),
          result.available(),
          result.lowestPrice(),
          result.highestPrice(),
          result.currency(),
          result.flightCount(),
          result.flights().stream()
              .map(FlightSearchResponse.FlightSearchItemResponse::from)
              .toList());
    }
  }
}
