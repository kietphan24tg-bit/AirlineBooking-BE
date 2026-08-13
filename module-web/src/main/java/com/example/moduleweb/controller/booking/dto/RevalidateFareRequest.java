package com.example.moduleweb.controller.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RevalidateFareRequest(
    @NotNull(message = "flightId is required") Long flightId,
    @NotNull(message = "fareId is required") Long fareId,
    String promoCode,
    @NotNull(message = "passengerSummary is required") @Valid PassengerSummaryRequest passengerSummary) {}
