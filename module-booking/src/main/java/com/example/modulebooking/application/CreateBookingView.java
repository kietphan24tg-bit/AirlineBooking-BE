package com.example.modulebooking.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateBookingView(
    BookingView booking,
    FlightView flight,
    FareView fare,
    PricingView pricing,
    PromotionView promotion,
    List<ContactView> contacts,
    PassengerSummaryView passengerSummary,
    List<PassengerView> passengers,
    SeatReservationView seatReservation,
    NextActionView nextAction) {

  public record BookingView(
      Long bookingId,
      String pnrCode,
      String status,
      OffsetDateTime expiresAt,
      OffsetDateTime createdAt) {}

  public record FlightView(
      Long flightId,
      String flightNumber,
      LocalDate flightDate,
      String status,
      AirlineView airline,
      FlightEndpointView departure,
      FlightEndpointView arrival) {}

  public record AirlineView(Long airlineId, String code, String name, String logoUrl) {}

  public record FlightEndpointView(
      Long airportId,
      String code,
      String name,
      String city,
      String country,
      String timeZone,
      String terminal,
      String gate,
      OffsetDateTime time) {}

  public record FareView(
      Long fareId,
      Long fareClassId,
      String status,
      String code,
      String name,
      String cabinClass,
      BigDecimal basePrice,
      BigDecimal taxes,
      BigDecimal totalPrice,
      String currency,
      Integer baggageCheckinKg,
      Integer baggageHandKg,
      boolean isRefundable,
      boolean isChangeable,
      BigDecimal changeFee,
      BigDecimal refundFee) {}

  public record PricingView(
      String currency,
      BigDecimal baseFare,
      BigDecimal taxes,
      BigDecimal fees,
      BigDecimal originalTotalPrice,
      BigDecimal discountAmount,
      Integer discountPercent,
      BigDecimal discountedTotalPrice) {}

  public record PromotionView(
      String promoCode, Long promotionId, BigDecimal discountAmount, Integer discountPercent) {}

  public record ContactView(Long contactId, String email, String phone, boolean primary) {}

  public record PassengerSummaryView(int adult, int child, int infant) {}

  public record PassengerView(
      Long passengerId,
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      String gender,
      String nationality,
      String passengerType,
      String identifyType,
      String identifyNumber,
      LocalDate expiryDate) {}

  public record SeatReservationView(
      int reservedSeatCount, String status, OffsetDateTime reservedUntil) {}

  public record NextActionView(String type, String endpoint) {}
}
