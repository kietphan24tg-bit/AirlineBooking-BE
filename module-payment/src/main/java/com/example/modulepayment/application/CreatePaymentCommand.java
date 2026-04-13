package com.example.modulepayment.application;

public record CreatePaymentCommand(Long bookingId, Long userId, String method) {}
