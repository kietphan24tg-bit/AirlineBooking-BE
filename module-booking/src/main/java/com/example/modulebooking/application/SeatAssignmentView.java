package com.example.modulebooking.application;

import java.math.BigDecimal;
import java.util.List;

public record SeatAssignmentView(
    Long bookingId,
    String status,
    BigDecimal seatSurchargeTotal,
    BigDecimal totalAmount,
    List<AssignmentView> assignments) {

  public record AssignmentView(
      Long passengerId,
      Long seatId,
      Long flightId,
      String seatNumber,
      String status,
      BigDecimal surchargeAmount,
      String currency) {}
}
