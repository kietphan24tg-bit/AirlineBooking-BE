package com.example.modulebooking.application;

import java.time.OffsetDateTime;
import java.util.List;

public record BookingTicketsView(Long bookingId, String pnrCode, List<TicketView> tickets) {

  public record TicketView(
      Long id,
      String ticketNumber,
      String status,
      OffsetDateTime issuedAt,
      PassengerView passenger,
      SeatView seat) {}

  public record PassengerView(
      Long id,
      String firstName,
      String lastName,
      String passengerType) {}

  public record SeatView(Long id, String seatNumber) {}
}
