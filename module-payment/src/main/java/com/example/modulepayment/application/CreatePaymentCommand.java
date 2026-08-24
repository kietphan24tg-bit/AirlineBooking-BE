package com.example.modulepayment.application;

public record CreatePaymentCommand(
    Long bookingId, Long userId, String method, String idempotencyKey) {

  public CreatePaymentCommand(Long bookingId, Long userId, String method) {
    this(bookingId, userId, method, null);
  }
}
