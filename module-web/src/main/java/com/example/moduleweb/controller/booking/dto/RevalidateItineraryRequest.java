package com.example.moduleweb.controller.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RevalidateItineraryRequest(
    @NotBlank(message = "tripType is required") String tripType,
    String promoCode,
    @NotNull(message = "passengerSummary is required") @Valid PassengerSummaryRequest passengerSummary,
    @NotEmpty(message = "segments is required") List<@Valid SegmentRequest> segments) {

  public record SegmentRequest(
      @NotBlank(message = "direction is required") String direction,
      @NotNull(message = "flightId is required") Long flightId,
      @NotNull(message = "fareId is required") Long fareId) {}
}
