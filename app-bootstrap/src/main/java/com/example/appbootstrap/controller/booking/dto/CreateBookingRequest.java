package com.example.appbootstrap.controller.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
    @NotBlank(message = "revalidateId is required") String revalidateId,
    @NotBlank(message = "idempotencyKey is required") String idempotencyKey,
    @NotNull(message = "passengerSummary is required") @Valid PassengerSummaryRequest passengerSummary,
    @NotBlank(message = "contactEmail is required") @Email(message = "contactEmail must be a valid email") String contactEmail,
    @NotBlank(message = "contactPhone is required") String contactPhone) {}
