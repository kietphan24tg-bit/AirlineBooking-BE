package com.example.appbootstrap.controller.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ServiceChangePreviewRequest(
    @NotEmpty(message = "passengers is required") List<@Valid PassengerServicesRequest> passengers) {

  public record PassengerServicesRequest(
      @NotNull(message = "passengerId is required")
          @Positive(message = "passengerId must be positive")
          Long passengerId,
      @NotNull(message = "selections is required") List<@Valid ServiceSelectionRequest> selections) {}

  public record ServiceSelectionRequest(
      @NotNull(message = "optionId is required")
          @Positive(message = "optionId must be positive")
          Long optionId,
      @NotNull(message = "quantity is required")
          @Positive(message = "quantity must be positive")
          Integer quantity) {}
}
