package com.example.moduleflight.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record FlightCalendarSearchQuery(
    FlightSearchTripType tripType,
    String fromAirportCode,
    String toAirportCode,
    LocalDate centerDate,
    int adult,
    int child,
    int infant,
    String promoCode,
    int limit,
    Set<CabinClass> cabinClasses,
    BigDecimal maxBudget,
    TimeOfDayBucket departureTimeBucket,
    TimeOfDayBucket arrivalTimeBucket,
    FlightConnectionFilter connectionFilter,
    FlightSortBy sortBy,
    int daysBefore,
    int daysAfter) {}
