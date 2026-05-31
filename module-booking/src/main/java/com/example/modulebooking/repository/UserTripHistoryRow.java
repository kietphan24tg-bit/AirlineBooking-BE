package com.example.modulebooking.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record UserTripHistoryRow(
    Long bookingId,
    Long flightId,
    String pnrCode,
    String bookingStatus,
    String holdStatus,
    String flightNumber,
    String cabinClass,
    String departureAirportCode,
    String departureCity,
    String arrivalAirportCode,
    String arrivalCity,
    OffsetDateTime departureTime,
    OffsetDateTime arrivalTime,
    LocalDate flightDate,
    Long passengerCount,
    BigDecimal totalAmount,
    String currency) {}
