package com.example.moduleweb.controller.checkin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record CheckInConfirmRequest(
    @NotBlank(message = "pnrCode is required") String pnrCode,
    @NotBlank(message = "lastName is required") String lastName,
    @NotEmpty(message = "segments is required") List<@Valid SegmentRequest> segments) {

  public record SegmentRequest(
      @NotNull(message = "flightId is required") @Positive(message = "flightId must be positive") Long flightId,
      @NotEmpty(message = "passengerIds is required") List<
                  @NotNull(message = "passengerIds contains null") @Positive(message = "passengerId must be positive") Long>
              passengerIds) {}
}
