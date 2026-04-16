package com.example.appbootstrap.controller.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PassengerSummaryRequest(
    @NotNull(message = "ADULT is required") @Min(value = 0, message = "ADULT must be >= 0") Integer adult,
    @NotNull(message = "CHILD is required") @Min(value = 0, message = "CHILD must be >= 0") Integer child,
    @NotNull(message = "INFANT is required") @Min(value = 0, message = "INFANT must be >= 0") Integer infant) {

  public int seatedPassengers() {
    return adult + child;
  }

  public int totalPassengers() {
    return adult + child + infant;
  }
}
