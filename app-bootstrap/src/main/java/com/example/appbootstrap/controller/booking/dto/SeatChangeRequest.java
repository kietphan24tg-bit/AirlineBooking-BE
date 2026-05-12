package com.example.appbootstrap.controller.booking.dto;

import jakarta.validation.constraints.NotNull;

public record SeatChangeRequest(
    @NotNull(message = "passengerId is required") Long passengerId,
    @NotNull(message = "fromSeatId is required") Long fromSeatId,
    Long toSeatId) {}
