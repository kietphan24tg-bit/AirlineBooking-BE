package com.example.appbootstrap.controller.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record SeatChangePreviewRequest(
    @NotEmpty(message = "changes is required") List<@Valid ChangeItemRequest> changes) {

  public record ChangeItemRequest(
      @NotNull(message = "passengerId is required") @Positive(message = "passengerId must be positive") Long passengerId,
      @NotNull(message = "flightId is required") @Positive(message = "flightId must be positive") Long flightId,
      @Positive(message = "fromSeatId must be positive") Long fromSeatId,
      @Positive(message = "toSeatId must be positive") Long toSeatId) {}
}
