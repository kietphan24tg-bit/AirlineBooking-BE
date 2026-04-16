package com.example.appbootstrap.controller.booking.dto;

import java.math.BigDecimal;

public record CreateBookingResponse(
    Long bookingId,
    String pnrCode,
    String status,
    String currency,
    BigDecimal totalAmount,
    BigDecimal discountAmount) {}
