package com.example.appbootstrap.controller.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PaymentStatusResponse(
    Long id,
    Long bookingId,
    BigDecimal amount,
    String currency,
    String method,
    String status,
    OffsetDateTime paidAt,
    OffsetDateTime expireAt,
    String transactionId,
    String qrData,
    String qrImageUrl,
    String paymentUrl,
    PaymentDetailsResponse details) {

  public PaymentStatusResponse(
      Long id,
      Long bookingId,
      BigDecimal amount,
      String currency,
      String method,
      String status,
      OffsetDateTime paidAt,
      OffsetDateTime expireAt,
      String transactionId,
      String qrData,
      String qrImageUrl,
      String paymentUrl) {
    this(
        id,
        bookingId,
        amount,
        currency,
        method,
        status,
        paidAt,
        expireAt,
        transactionId,
        qrData,
        qrImageUrl,
        paymentUrl,
        null);
  }

  public record PaymentDetailsResponse(FlightResponse flight, PriceBreakdownResponse price) {}

  public record FlightResponse(
      String flightNumber,
      String flightName,
      String airline,
      String departureCode,
      String departureCity,
      String departureName,
      String arrivalCode,
      String arrivalCity,
      String arrivalName,
      LocalDate flightDate,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime) {}

  public record PriceBreakdownResponse(
      BigDecimal baseFare,
      BigDecimal discount,
      BigDecimal tax,
      BigDecimal serviceFee,
      BigDecimal seatFee,
      BigDecimal total,
      String currency) {}
}
