package com.example.moduleweb.controller.flight.dto;

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

public record FlightCalendarSearchQueryRequest(
    @Schema(description = "Trip type: currently ONE_WAY only", example = "ONE_WAY")
        @NotBlank(message = "tripType is required") String tripType,
    @Schema(description = "Departure airport IATA code", example = "SGN")
        @NotBlank(message = "from is required") String from,
    @Schema(description = "Arrival airport IATA code", example = "HAN")
        @NotBlank(message = "to is required") String to,
    @Schema(description = "Center departure date (yyyy-MM-dd)", example = "2026-05-26")
        @NotNull(message = "centerDate is required") LocalDate centerDate,
    @Schema(description = "Adult passengers (1-9)", example = "1")
        @NotNull(message = "adult required") @Min(value = 1, message = "min 1") @Max(value = 9, message = "max 9") Integer adult,
    @Schema(description = "Child passengers (0-9)", example = "0")
        @Min(value = 0, message = "child must be 0-9") @Max(value = 9, message = "child must be 0-9") Integer child,
    @Schema(description = "Infant passengers (0-9, must not exceed adults)", example = "0")
        @Min(value = 0, message = "infant must be 0-9") @Max(value = 9, message = "infant must be 0-9") Integer infant,
    @Schema(description = "Optional promo code", example = "SUMMER26") String promoCode,
    @Schema(description = "Page size per day (1-100), defaults to 10", example = "10")
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
                "MORNING, AFTERNOON, EVENING, ANY, or omit - local time at departure airport.",
            example = "MORNING")
        String departureTimeBucket,
    @Schema(
            description = "Same as departureTimeBucket for arrival local time.",
            example = "EVENING")
        String arrivalTimeBucket,
    @Schema(
            description = "ANY (default) or DIRECT_ONLY - single-leg flights only for now.",
            example = "ANY")
        String connectionFilter,
    @Schema(
            description =
                "RECOMMENDED (default), CHEAPEST, FASTEST, or BEST_EXPERIENCE for sorting returned flights.",
            example = "CHEAPEST")
        String sortBy,
    @Schema(
            description = "How many days before centerDate to include, defaults to 3",
            example = "3")
        @Min(value = 0, message = "daysBefore must be >= 0") Integer daysBefore,
    @Schema(description = "How many days after centerDate to include, defaults to 3", example = "3")
        @Min(value = 0, message = "daysAfter must be >= 0") Integer daysAfter) {

  public int effectiveLimit() {
    return limit == null ? 10 : limit;
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

  public int effectiveDaysBefore() {
    return daysBefore == null ? 3 : daysBefore;
  }

  public int effectiveDaysAfter() {
    return daysAfter == null ? 3 : daysAfter;
  }
}
