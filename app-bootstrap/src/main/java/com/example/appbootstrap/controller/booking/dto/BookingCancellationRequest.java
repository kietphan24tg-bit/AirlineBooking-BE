package com.example.appbootstrap.controller.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public record BookingCancellationRequest(
    @NotEmpty(message = "passengerIds is required")
        List<@NotNull(message = "passengerIds contains null") @Positive(message = "passengerId must be positive") Long>
            passengerIds,
    String reason,
    @NotBlank(message = "idempotencyKey is required") String idempotencyKey,
    @NotNull(message = "expectedNetRefund is required")
        @DecimalMin(value = "0.00", inclusive = true, message = "expectedNetRefund must be non-negative")
        BigDecimal expectedNetRefund,
    @NotNull(message = "expectedRefundFee is required")
        @DecimalMin(value = "0.00", inclusive = true, message = "expectedRefundFee must be non-negative")
        BigDecimal expectedRefundFee) {}
