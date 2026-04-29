package com.example.modulepayment.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PaymentDetailsView(FlightView flight, PriceBreakdownView price) {

  public record FlightView(
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

  public record PriceBreakdownView(
      BigDecimal baseFare,
      BigDecimal discount,
      BigDecimal tax,
      BigDecimal serviceFee,
      BigDecimal seatFee,
      BigDecimal total,
      String currency) {}
}
