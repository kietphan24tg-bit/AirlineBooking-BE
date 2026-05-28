package com.example.appbootstrap.controller.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FlightChangeConfirmRequest(
    @NotBlank(message = "quoteToken is required") String quoteToken,
    @NotBlank(message = "idempotencyKey is required") String idempotencyKey,
    @NotNull(message = "expectedAdditionalAmount is required") @DecimalMin(
            value = "0.00",
            inclusive = true,
            message = "expectedAdditionalAmount must be non-negative")
        BigDecimal expectedAdditionalAmount,
    @NotNull(message = "expectedRefundAmount is required") @DecimalMin(
            value = "0.00",
            inclusive = true,
            message = "expectedRefundAmount must be non-negative")
        BigDecimal expectedRefundAmount,
    @NotNull(message = "expectedNetAmount is required") BigDecimal expectedNetAmount) {}
