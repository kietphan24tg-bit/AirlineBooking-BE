package com.example.moduleweb.controller.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SeatAssignmentRequest(
    @NotEmpty(message = "assignments is required") List<@Valid AssignmentRequest> assignments) {

  public record AssignmentRequest(
      @NotNull(message = "passengerId is required") Long passengerId,
      @NotNull(message = "seatId is required") Long seatId) {}
}
