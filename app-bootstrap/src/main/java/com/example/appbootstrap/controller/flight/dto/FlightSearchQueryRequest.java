package com.example.appbootstrap.controller.flight.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FlightSearchQueryRequest(
    @NotBlank(message = "tripType is required") String tripType,
    @NotBlank(message = "from is required") String from,
    @NotBlank(message = "to is required") String to,
    @NotNull(message = "departureDate is required") LocalDate departureDate,
    LocalDate returnDate,
    @NotNull(message = "adult required") @Min(value = 1, message = "min 1") @Max(value = 9, message = "max 9") Integer adult,
    @Min(value = 0, message = "child must be 0-9") @Max(value = 9, message = "child must be 0-9") Integer child,
    @Min(value = 0, message = "infant must be 0-9") @Max(value = 9, message = "infant must be 0-9") Integer infant,
    String promoCode,
    String cursor,
    String outboundCursor,
    String inboundCursor,
    @Min(value = 1, message = "limit must be 1-100") @Max(value = 100, message = "limit must be 1-100") Integer limit,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Cabin filters (repeat param), e.g. cabinClasses=ECONOMY&cabinClasses=BUSINESS"))
        List<String> cabinClasses,
    @Schema(
            description =
                "Maximum total price for all passengers in the route currency; compared to effective fare (after promo).")
        @Positive(message = "maxBudget must be greater than 0") BigDecimal maxBudget,
    @Schema(
            description =
                "MORNING, AFTERNOON, EVENING, ANY, or omit — local time at departure airport.")
        String departureTimeBucket,
    @Schema(description = "Same as departureTimeBucket for arrival local time.")
        String arrivalTimeBucket,
    @Schema(description = "ANY (default) or DIRECT_ONLY — single-leg flights only for now.")
        String connectionFilter) {
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
