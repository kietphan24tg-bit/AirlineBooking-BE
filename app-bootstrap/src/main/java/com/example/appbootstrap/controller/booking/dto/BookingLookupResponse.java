package com.example.appbootstrap.controller.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingLookupResponse(
    BookingSummary booking,
    PrimaryContact primaryContact,
    PaymentDetails paymentDetails,
    List<ItinerarySummary> itineraries,
    List<PassengerSummary> passengers,
    Actions actions) {

  public record BookingSummary(
      Long bookingId,
      String pnrCode,
      String status,
      String statusLabel,
      BigDecimal totalAmount,
      String currency,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt) {}

  public record PrimaryContact(String email, String phone) {}

  public record PaymentDetails(PriceBreakdown price) {}

  public record PriceBreakdown(
      BigDecimal baseFare,
      BigDecimal discount,
      BigDecimal tax,
      BigDecimal serviceFee,
      BigDecimal seatFee,
      BigDecimal total,
      String currency) {}

  public record ItinerarySummary(
      Long flightId,
      String flightNumber,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime,
      String departureAirportCode,
      String departureCity,
      String departureTerminal,
      String arrivalAirportCode,
      String arrivalCity,
      String arrivalTerminal,
      String flightStatus,
      String fareName,
      String cabinClass) {}

  public record PassengerSummary(
      Long passengerId,
      String firstName,
      String lastName,
      String passengerType,
      String gender,
      LocalDate dateOfBirth) {}

  public record Actions(
      boolean canSelectSeat,
      boolean canBuyAncillaries,
      boolean canCheckIn,
      boolean canChangeFlight,
      boolean canCancel) {}
}
