package com.example.modulepayment.application;

public record PaymentResultStatusView(
    Long paymentId,
    Long bookingId,
    String paymentStatus,
    String bookingStatus,
    String resultPage,
    PollingView polling,
    FailureView failure) {

  public record PollingView(Boolean shouldPoll, Integer nextPollAfterSeconds) {}

  public record FailureView(String code, String message) {}
}
