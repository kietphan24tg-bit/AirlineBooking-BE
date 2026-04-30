package com.example.appbootstrap.controller.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(name = "BookingTicketsResponse", description = "Confirmed booking PNR and issued tickets")
public record BookingTicketsResponse(Long bookingId, String pnrCode, List<TicketResponse> tickets) {

  public record TicketResponse(
      Long id,
      String ticketNumber,
      String status,
      OffsetDateTime issuedAt,
      PassengerResponse passenger,
      SeatResponse seat) {}

  public record PassengerResponse(
      Long id,
      String firstName,
      String lastName,
      String passengerType) {}

  public record SeatResponse(Long id, String seatNumber) {}
}
