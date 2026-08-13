package com.example.modulepayment.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CreatePaymentResponse(PaymentResponse payment, PaymentActionResponse action) {

  public record PaymentResponse(
      Long id,
      Long bookingId,
      BigDecimal amount,
      String currency,
      String method,
      String status,
      OffsetDateTime expireAt,
      String transactionId,
      String qrData,
      PaymentDetailsResponse details) {

    public PaymentResponse(
        Long id,
        Long bookingId,
        BigDecimal amount,
        String currency,
        String method,
        String status,
        OffsetDateTime expireAt,
        String transactionId,
        String qrData) {
      this(id, bookingId, amount, currency, method, status, expireAt, transactionId, qrData, null);
    }
  }

  public record PaymentActionResponse(
      String type, String qrData, String qrImageUrl, String paymentUrl) {}

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
