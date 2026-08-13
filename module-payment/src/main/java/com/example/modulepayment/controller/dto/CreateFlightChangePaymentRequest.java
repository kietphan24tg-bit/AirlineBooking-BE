package com.example.modulepayment.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateFlightChangePaymentRequest(
    @NotNull(message = "bookingId is required") Long bookingId,
    @NotNull(message = "settlementId is required") @Positive(message = "settlementId must be positive") Long flightChangeSettlementId,
    @NotBlank(message = "method is required") String method) {}
