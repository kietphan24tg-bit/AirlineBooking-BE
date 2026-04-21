package com.example.moduleflight.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record FlightSearchQuery(
    FlightSearchTripType tripType,
    String fromAirportCode,
    String toAirportCode,
    LocalDate departureDate,
    LocalDate returnDate,
    int adult,
    int child,
    int infant,
    String promoCode,
    String cursor,
    String outboundCursor,
    String inboundCursor,
    int limit,
    Set<CabinClass> cabinClasses,
    BigDecimal maxBudget,
    TimeOfDayBucket departureTimeBucket,
    TimeOfDayBucket arrivalTimeBucket,
    FlightConnectionFilter connectionFilter,
    FlightSortBy sortBy) {}
