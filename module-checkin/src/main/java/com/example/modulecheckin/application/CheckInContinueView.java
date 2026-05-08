package com.example.modulecheckin.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CheckInContinueView(
    BookingView booking, List<SegmentView> segments, SummaryView summary, ActionsView actions) {

  public record BookingView(
      Long bookingId,
      String pnrCode,
      String status,
      String contactEmail,
      String contactPhone,
      int passengerCount,
      String currency,
      String lastName) {}

  public record SegmentView(
      Long flightId,
      String flightNumber,
      String journeyType,
      String status,
      CheckInWindowView checkInWindow,
      EndpointView departure,
      EndpointView arrival,
      AirlineView airline,
      FareView fare,
      List<PassengerView> passengers) {}

  public record CheckInWindowView(
      String status, OffsetDateTime opensAt, OffsetDateTime closesAt, String message) {}

  public record EndpointView(
      String airportCode,
      String airportName,
      String city,
      String terminal,
      String gate,
      OffsetDateTime time) {}

  public record AirlineView(String code, String name, String logoUrl) {}

  public record FareView(String fareClass, String cabinClass) {}

  public record PassengerView(
      Long passengerId,
      Long ticketId,
      String ticketNumber,
      String fullName,
      LocalDate dateOfBirth,
      String passengerType,
      boolean eligible,
      String checkInStatus,
      SeatView seat,
      BaggageView baggage,
      List<ServiceView> services,
      List<String> warnings) {}

  public record SeatView(boolean assigned, Long seatId, String seatNumber) {}

  public record BaggageView(Integer cabinKg, Integer checkedKg) {}

  public record ServiceView(String code, String name, Integer quantity, String status) {}

  public record SummaryView(
      int selectedSegments,
      int selectedPassengers,
      int readyPassengers,
      int blockedPassengers,
      int passengersWithoutSeat) {}

  public record ActionsView(
      boolean allowConfirmCheckIn, boolean allowSeatSelection, String nextStep) {}
}
