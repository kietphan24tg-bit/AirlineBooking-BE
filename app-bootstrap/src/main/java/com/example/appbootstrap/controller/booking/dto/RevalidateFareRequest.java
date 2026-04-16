package com.example.appbootstrap.controller.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RevalidateFareRequest(
    @NotNull(message = "flightId is required") Long flightId,
    @NotNull(message = "fareId is required") Long fareId,
    @DecimalMin(value = "0.00", message = "expectedPrice must be >= 0") BigDecimal expectedPrice,
    String promoCode,
    @NotNull(message = "passengerSummary is required") @Valid PassengerSummaryRequest passengerSummary) {}
