package com.example.appbootstrap.controller.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

public record TripHistoryQueryRequest(
    @Schema(
            description = "Trip status filter: UPCOMING, COMPLETED, or CANCELLED",
            example = "UPCOMING")
        String status,
    @Schema(description = "Search by PNR, flight number, airport code, or city", example = "ABC123")
        String query,
    @Schema(description = "Inclusive start flight date filter (yyyy-MM-dd)", example = "2026-06-01")
        LocalDate fromDate,
    @Schema(description = "Inclusive end flight date filter (yyyy-MM-dd)", example = "2026-06-30")
        LocalDate toDate,
    @Schema(description = "Cursor for pagination continuation") String cursor,
    @Schema(description = "Page size (1-100), defaults to 20", example = "20")
        @Min(value = 1, message = "limit must be 1-100") @Max(value = 100, message = "limit must be 1-100") Integer limit) {

  public int effectiveLimit() {
    return limit == null ? 20 : limit;
  }
}
