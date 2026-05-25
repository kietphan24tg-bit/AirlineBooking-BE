package com.example.appbootstrap.controller.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateServiceChangePaymentRequest(
    @NotNull(message = "bookingId is required") Long bookingId,
    @NotNull(message = "settlementId is required")
        @Positive(message = "settlementId must be positive")
        Long serviceChangeSettlementId,
    @NotBlank(message = "method is required") String method) {}
