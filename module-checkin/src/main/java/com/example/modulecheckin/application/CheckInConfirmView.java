package com.example.modulecheckin.application;

import java.time.OffsetDateTime;
import java.util.List;

public record CheckInConfirmView(
    BookingView booking,
    List<SegmentView> segments,
    List<BoardingPassView> boardingPasses,
    SummaryView summary) {

  public record BookingView(Long bookingId, String pnrCode, String status) {}

  public record SegmentView(Long flightId, String flightNumber, List<PassengerView> passengers) {}

  public record PassengerView(
      Long passengerId,
      Long ticketId,
      String ticketNumber,
      String fullName,
      String seatNumber,
      Long boardingPassId,
      String checkInStatus) {}

  public record BoardingPassView(
      Long boardingPassId,
      Long ticketId,
      Long passengerId,
      Long flightId,
      String qrCode,
      String gate,
      OffsetDateTime boardingTime,
      Integer sequenceNumber,
      String seatLabel) {}

  public record SummaryView(int confirmedPassengers, int confirmedSegments) {}
}
