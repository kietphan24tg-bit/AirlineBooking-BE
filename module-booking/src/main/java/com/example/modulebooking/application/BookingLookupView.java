package com.example.modulebooking.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingLookupView(
    BookingSummary booking,
    PrimaryContact primaryContact,
    PaymentDetails paymentDetails,
    List<ItinerarySummary> itineraries,
    List<PassengerSummary> passengers,
    Actions actions) {

  public record BookingSummary(
      Long bookingId,
      String pnrCode,
      String status,
      String statusLabel,
      BigDecimal totalAmount,
      String currency,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt) {}

  public record PrimaryContact(String email, String phone) {}

  public record PaymentDetails(PriceBreakdown price) {}

  public record PriceBreakdown(
      BigDecimal baseFare,
      BigDecimal discount,
      BigDecimal tax,
      BigDecimal serviceFee,
      BigDecimal seatFee,
      BigDecimal total,
      String currency) {}

  public record ItinerarySummary(
      Long flightId,
      String flightNumber,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime,
      String departureAirportCode,
      String departureCity,
      String departureTerminal,
      String arrivalAirportCode,
      String arrivalCity,
      String arrivalTerminal,
      String flightStatus,
      String fareName,
      String cabinClass,
      FareBenefits fareBenefits) {}

  public record FareBenefits(
      String fareClassCode,
      String fareClassName,
      String cabinClass,
      Integer baggageCheckinKg,
      Integer baggageHandKg,
      BigDecimal changeFee,
      BigDecimal refundFee,
      boolean refundable,
      boolean changeable,
      boolean priorityCheckin,
      boolean priorityBoarding,
      boolean loungeAccess,
      boolean seatSelection,
      String seatSelectionPolicy,
      BigDecimal mileageRate,
      String mealType,
      boolean wifiIncluded,
      boolean fastTrackSecurity,
      IncludedSeatCategory includedSeatCategory) {}

  public record IncludedSeatCategory(Long id, String code, String name) {}

  public record SeatAssignmentDetail(
      Long flightId,
      Long seatId,
      String seatNumber,
      String status,
      BigDecimal surchargeAmount,
      String currency) {}

  public record ServiceSelectionDetail(
      Long serviceId,
      String serviceType,
      String serviceName,
      Long optionId,
      String optionName,
      Integer quantity,
      BigDecimal price) {}

  public record PassengerSelections(
      List<SeatAssignmentDetail> seats, List<ServiceSelectionDetail> services) {}

  public record PassengerSummary(
      Long passengerId,
      String firstName,
      String lastName,
      String passengerType,
      String gender,
      LocalDate dateOfBirth,
      PassengerSelections selections) {}

  public record Actions(
      boolean canSelectSeat,
      boolean canBuyAncillaries,
      boolean canCheckIn,
      boolean canChangeFlight,
      boolean canCancel) {}
}
