package com.example.appbootstrap.controller.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateBookingResponse(
    BookingResponse booking,
    FlightResponse flight,
    FareResponse fare,
    PricingResponse pricing,
    PromotionResponse promotion,
    List<ContactResponse> contacts,
    PassengerSummaryResponse passengerSummary,
    List<PassengerResponse> passengers,
    SeatReservationResponse seatReservation,
    NextActionResponse nextAction) {

  public record BookingResponse(
      Long bookingId,
      String pnrCode,
      String status,
      OffsetDateTime expiresAt,
      OffsetDateTime createdAt) {}

  public record FlightResponse(
      Long flightId,
      String flightNumber,
      LocalDate flightDate,
      String status,
      AirlineResponse airline,
      FlightEndpointResponse departure,
      FlightEndpointResponse arrival) {}

  public record AirlineResponse(Long airlineId, String code, String name, String logoUrl) {}

  public record FlightEndpointResponse(
      Long airportId,
      String code,
      String name,
      String city,
      String country,
      String timeZone,
      String terminal,
      String gate,
      OffsetDateTime time) {}

  public record FareResponse(
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

  public record PricingResponse(
      String currency,
      BigDecimal baseFare,
      BigDecimal taxes,
      BigDecimal fees,
      BigDecimal originalTotalPrice,
      BigDecimal discountAmount,
      Integer discountPercent,
      BigDecimal discountedTotalPrice) {}

  public record PromotionResponse(
      String promoCode, Long promotionId, BigDecimal discountAmount, Integer discountPercent) {}

  public record ContactResponse(Long contactId, String email, String phone, boolean primary) {}

  public record PassengerSummaryResponse(int adult, int child, int infant) {}

  public record PassengerResponse(
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

  public record SeatReservationResponse(
      int reservedSeatCount, String status, OffsetDateTime reservedUntil) {}

  public record NextActionResponse(String type, String endpoint) {}
}
