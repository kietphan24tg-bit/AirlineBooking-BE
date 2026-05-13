package com.example.modulepayment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PaymentResultSummaryView(
    Long paymentId,
    Long bookingId,
    String paymentStatus,
    String bookingStatus,
    BookingView booking,
    ContactView contact,
    PaymentView payment,
    SummaryView summary,
    List<SegmentView> segments,
    List<PassengerView> passengers,
    List<TicketView> tickets,
    FareBreakdownView fareBreakdown) {

  public record BookingView(
      String pnrCode,
      OffsetDateTime createdAt,
      OffsetDateTime paidAt,
      OffsetDateTime expiresAt,
      String currency,
      BigDecimal totalAmount) {}

  public record ContactView(String fullName, String email, String phone) {}

  public record PaymentView(
      String method, String transactionId, BigDecimal amount, String currency) {}

  public record SummaryView(
      Integer passengerCount, Integer segmentCount, BigDecimal totalAmount, String currency) {}

  public record SegmentView(
      String segmentKey,
      Long flightId,
      String journeyType,
      String flightNumber,
      String airlineName,
      LocationView departure,
      LocationView arrival,
      Integer durationMinutes,
      Integer stops,
      FareView fare) {}

  public record LocationView(
      String airportCode,
      String airportName,
      String city,
      String terminal,
      OffsetDateTime time) {}

  public record FareView(
      String fareClassCode,
      String fareClassName,
      String cabinClass,
      Integer checkedBaggageKg,
      Integer carryOnBaggageKg) {}

  public record PassengerView(
      Long passengerId,
      String title,
      String firstName,
      String lastName,
      String fullName,
      String passengerType,
      List<PassengerSegmentView> segments,
      List<PassengerServiceView> services) {}

  public record PassengerSegmentView(String segmentKey, SeatView seat, EmbeddedTicketView ticket) {}

  public record SeatView(
      Boolean assigned, Long seatId, String seatNumber, String source) {}

  public record EmbeddedTicketView(
      Long ticketId, String ticketNumber, String status, OffsetDateTime issuedAt) {}

  public record PassengerServiceView(
      Long serviceId,
      String serviceType,
      String serviceName,
      Long optionId,
      String optionName,
      Integer quantity,
      BigDecimal amount,
      String currency,
      String segmentKey) {}

  public record TicketView(
      Long ticketId,
      String ticketNumber,
      String status,
      OffsetDateTime issuedAt,
      Long passengerId,
      String segmentKey,
      Long flightId,
      SeatView seat) {}

  public record FareBreakdownView(
      String currency, FareTotalsView totals, List<PassengerFareBreakdownView> byPassenger) {}

  public record FareTotalsView(
      BigDecimal baseFare,
      BigDecimal discount,
      BigDecimal tax,
      BigDecimal serviceFee,
      BigDecimal seatFee,
      BigDecimal total) {}

  public record PassengerFareBreakdownView(
      Long passengerId,
      String passengerLabel,
      List<PassengerSegmentFareBreakdownView> bySegment,
      BigDecimal passengerTotal) {}

  public record PassengerSegmentFareBreakdownView(
      String segmentKey,
      String routeLabel,
      SegmentFareAmountsView fare,
      List<LabeledAmountView> taxesAndFees,
      List<LabeledAmountView> services,
      BigDecimal segmentTotal) {}

  public record SegmentFareAmountsView(
      String fareClassCode, BigDecimal baseFare, BigDecimal vatOnFare, BigDecimal fareTotal) {}

  public record LabeledAmountView(String label, BigDecimal amount) {}
}
