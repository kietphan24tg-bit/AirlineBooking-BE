package com.example.appbootstrap.controller.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record BookingCancellationPreviewRequest(
    @NotEmpty(message = "ticketIds is required")
        List<
                @NotNull(message = "ticketIds contains null")
                @Positive(message = "ticketId must be positive")
                Long>
            ticketIds) {}
