package com.example.moduleweb.controller.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingLookupRequest(
    @NotBlank(message = "pnrCode is required") @Size(max = 20, message = "pnrCode must be at most 20 characters") String pnrCode,
    @NotBlank(message = "lastName is required") @Size(max = 100, message = "lastName must be at most 100 characters") String lastName) {}
