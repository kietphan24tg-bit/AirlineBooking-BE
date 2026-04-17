package com.example.appbootstrap.controller.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CreateBookingRequest(
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
      @NotBlank(message = "nationality is required") String nationality,
      @NotBlank(message = "passengerType is required") String passengerType,
      @NotBlank(message = "identifyType is required") String identifyType,
      @NotBlank(message = "identifyNumber is required") String identifyNumber,
      LocalDate expiryDate) {}
}
