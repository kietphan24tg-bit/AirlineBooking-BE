package com.example.moduleweb.controller.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record FlightChangeQuoteRequest(
    @NotEmpty(message = "ticketIds is required") List<
                @NotNull(message = "ticketIds contains null") @Positive(message = "ticketId must be positive") Long>
            ticketIds,
    @NotNull(message = "currentFlightId is required") @Positive(message = "currentFlightId must be positive") Long currentFlightId,
    @NotNull(message = "newFlightId is required") @Positive(message = "newFlightId must be positive") Long newFlightId,
    @NotNull(message = "newFareId is required") @Positive(message = "newFareId must be positive") Long newFareId) {}
