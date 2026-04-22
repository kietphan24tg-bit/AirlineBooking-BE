package com.example.appbootstrap.controller.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CreateBookingRequest(
    @Schema(
            description = "Active revalidateId returned by /api/flights/revalidate-fare.",
            example = "RV_ABC123DEF456")
        @NotBlank(message = "revalidateId is required") String revalidateId,
    @NotBlank(message = "idempotencyKey is required") String idempotencyKey,
    @NotNull(message = "passengerSummary is required") @Valid PassengerSummaryRequest passengerSummary,
    @NotEmpty(message = "contacts is required") List<@Valid BookingContactRequest> contacts,
    @NotEmpty(message = "passengers is required") List<@Valid BookingPassengerRequest> passengers) {

  public record BookingContactRequest(
      @Email(message = "contact email must be a valid email") String email,
      String phone,
      Boolean primary) {}

  public record BookingPassengerRequest(
      @NotBlank(message = "firstName is required") String firstName,
      @NotBlank(message = "lastName is required") String lastName,
      @NotNull(message = "dateOfBirth is required") LocalDate dateOfBirth,
      @NotBlank(message = "gender is required") String gender,
      @Schema(description = "Required for ADULT. Optional for CHILD and INFANT.", nullable = true)
          String nationality,
      @Schema(
              description =
                  "Passenger type. Must match age on flightDate: ADULT >= 12, CHILD = 2-11, INFANT < 2.")
          @NotBlank(message = "passengerType is required") String passengerType,
      @Schema(
              description =
                  "Required for ADULT. Optional for CHILD and INFANT. Must be ID_CARD or PASSPORT when provided.",
              nullable = true)
          String identifyType,
      @Schema(description = "Required for ADULT. Optional for CHILD and INFANT.", nullable = true)
          String identifyNumber,
      LocalDate expiryDate) {}
}
