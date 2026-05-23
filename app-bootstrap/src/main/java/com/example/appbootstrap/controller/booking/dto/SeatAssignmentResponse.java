package com.example.appbootstrap.controller.booking.dto;

import java.math.BigDecimal;
import java.util.List;

public record SeatAssignmentResponse(
    Long bookingId,
    String status,
    BigDecimal seatSurchargeTotal,
    BigDecimal totalAmount,
    List<AssignmentResponse> assignments) {

  public record AssignmentResponse(
      Long passengerId,
      Long seatId,
      Long flightId,
      String seatNumber,
      String status,
      BigDecimal surchargeAmount,
      String currency) {}
}
