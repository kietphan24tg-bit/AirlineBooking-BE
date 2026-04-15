package com.example.moduleflight.application;

import java.time.LocalDate;

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
    int limit) {}
