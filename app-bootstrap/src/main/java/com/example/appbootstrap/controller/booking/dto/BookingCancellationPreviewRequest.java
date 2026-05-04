package com.example.appbootstrap.controller.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record BookingCancellationPreviewRequest(
    @NotEmpty(message = "passengerIds is required")
        List<@NotNull(message = "passengerIds contains null") @Positive(message = "passengerId must be positive") Long>
            passengerIds) {}
