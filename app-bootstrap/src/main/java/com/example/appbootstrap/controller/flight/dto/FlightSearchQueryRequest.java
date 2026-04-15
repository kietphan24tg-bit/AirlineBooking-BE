package com.example.appbootstrap.controller.flight.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record FlightSearchQueryRequest(
    @NotBlank(message = "tripType is required") String tripType,
    @NotBlank(message = "from is required") String from,
    @NotBlank(message = "to is required") String to,
    @NotNull(message = "departureDate is required") LocalDate departureDate,
    LocalDate returnDate,
    @NotNull(message = "adult is required")
        @Min(value = 1, message = "adult must be at least 1")
        @Max(value = 9, message = "adult must not exceed 9")
        Integer adult,
    @Min(value = 0, message = "child must be at least 0")
        @Max(value = 9, message = "child must not exceed 9")
        Integer child,
    @Min(value = 0, message = "infant must be at least 0")
        @Max(value = 9, message = "infant must not exceed 9")
        Integer infant,
    String promoCode,
    String cursor,
    String outboundCursor,
    String inboundCursor,
    @Min(value = 1, message = "limit must be at least 1")
        @Max(value = 100, message = "limit must not exceed 100")
        Integer limit) {
  public int effectiveLimit() {
    return limit == null ? 20 : limit;
  }

  public int effectiveChild() {
    return child == null ? 0 : child;
  }

  public int effectiveInfant() {
    return infant == null ? 0 : infant;
  }

  public int totalPassengers() {
    return adult + effectiveChild() + effectiveInfant();
  }
}
