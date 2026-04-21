package com.example.appbootstrap.controller.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PassengerSummaryRequest(
    @Schema(description = "Adult passengers aged 12 or above.", example = "1")
        @NotNull(message = "ADULT is required") @Min(value = 0, message = "ADULT must be >= 0") Integer adult,
    @Schema(
            description =
                "Child passengers aged 2-11, priced at 75% of the adult fare and requiring seats.",
            example = "0")
        @NotNull(message = "CHILD is required") @Min(value = 0, message = "CHILD must be >= 0") Integer child,
    @Schema(
            description =
                "Infants under 2 years old, priced at 10% of the adult fare and not requiring seats.",
            example = "0")
        @NotNull(message = "INFANT is required") @Min(value = 0, message = "INFANT must be >= 0") Integer infant) {

  /** Adults and children need seats; infants do not. */
  public int seatedPassengers() {
    return adult + child;
  }

  public int totalPassengers() {
    return adult + child + infant;
  }
}
