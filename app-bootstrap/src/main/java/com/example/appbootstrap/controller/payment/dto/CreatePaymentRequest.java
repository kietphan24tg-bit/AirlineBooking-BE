package com.example.appbootstrap.controller.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(
    @NotNull(message = "bookingId is required") Long bookingId,
    @NotBlank(message = "method is required") String method) {}
