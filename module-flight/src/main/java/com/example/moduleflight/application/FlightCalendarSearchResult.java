package com.example.moduleflight.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FlightCalendarSearchResult(
    LocalDate centerDate,
    LocalDate selectedDate,
    String currency,
    List<CalendarDayResult> days,
    CalendarDayResult selectedDay) {

  public record CalendarDayResult(
      LocalDate date,
      boolean available,
      BigDecimal lowestPrice,
      BigDecimal highestPrice,
      String currency,
      int flightCount,
      List<FlightSearchResult.FlightOptionView> flights) {}
}
