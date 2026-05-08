package com.example.appbootstrap.controller.checkin.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CheckInContinueResponse(
    BookingResponse booking,
    List<SegmentResponse> segments,
    SummaryResponse summary,
    ActionsResponse actions) {

  public record BookingResponse(
      Long bookingId,
      String pnrCode,
      String status,
      String contactEmail,
      String contactPhone,
      int passengerCount,
      String currency,
      String lastName) {}

  public record SegmentResponse(
      Long flightId,
      String flightNumber,
      String journeyType,
      String status,
      CheckInWindowResponse checkInWindow,
      EndpointResponse departure,
      EndpointResponse arrival,
      AirlineResponse airline,
      FareResponse fare,
      List<PassengerResponse> passengers) {}

  public record CheckInWindowResponse(
      String status, OffsetDateTime opensAt, OffsetDateTime closesAt, String message) {}

  public record EndpointResponse(
      String airportCode,
      String airportName,
      String city,
      String terminal,
      String gate,
      OffsetDateTime time) {}

  public record AirlineResponse(String code, String name, String logoUrl) {}

  public record FareResponse(String fareClass, String cabinClass) {}

  public record PassengerResponse(
      Long passengerId,
      Long ticketId,
      String ticketNumber,
      String fullName,
      LocalDate dateOfBirth,
      String passengerType,
      boolean eligible,
      String checkInStatus,
      SeatResponse seat,
      BaggageResponse baggage,
      List<ServiceResponse> services,
      List<String> warnings) {}

  public record SeatResponse(boolean assigned, Long seatId, String seatNumber) {}

  public record BaggageResponse(Integer cabinKg, Integer checkedKg) {}

  public record ServiceResponse(String code, String name, Integer quantity, String status) {}

  public record SummaryResponse(
      int selectedSegments,
      int selectedPassengers,
      int readyPassengers,
      int blockedPassengers,
      int passengersWithoutSeat) {}

  public record ActionsResponse(
      boolean allowConfirmCheckIn, boolean allowSeatSelection, String nextStep) {}
}
