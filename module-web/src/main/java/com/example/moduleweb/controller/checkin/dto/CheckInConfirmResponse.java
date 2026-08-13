package com.example.moduleweb.controller.checkin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CheckInConfirmResponse(
    BookingResponse booking,
    List<SegmentResponse> segments,
    List<BoardingPassResponse> boardingPasses,
    SummaryResponse summary) {

  public record BookingResponse(Long bookingId, String pnrCode, String status) {}

  public record SegmentResponse(
      Long flightId, String flightNumber, List<PassengerResponse> passengers) {}

  public record PassengerResponse(
      Long passengerId,
      Long ticketId,
      String ticketNumber,
      String fullName,
      String seatNumber,
      Long boardingPassId,
      String checkInStatus) {}

  public record BoardingPassResponse(
      Long boardingPassId,
      Long ticketId,
      Long passengerId,
      Long flightId,
      String qrCode,
      String gate,
      OffsetDateTime boardingTime,
      Integer sequenceNumber,
      String seatLabel) {}

  public record SummaryResponse(int confirmedPassengers, int confirmedSegments) {}
}
