package com.example.moduleweb.controller.booking.dto;

import com.example.modulebooking.application.BookingLookupView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingLookupResponse(
    BookingSummary booking,
    PrimaryContact primaryContact,
    PaymentDetails paymentDetails,
    List<ItinerarySummary> itineraries,
    List<PassengerSummary> passengers,
    Actions actions) {

  public static BookingLookupResponse from(BookingLookupView view) {
    return new BookingLookupResponse(
        toBookingSummary(view.booking()),
        new PrimaryContact(view.primaryContact().email(), view.primaryContact().phone()),
        new PaymentDetails(toPriceBreakdown(view.paymentDetails().price())),
        view.itineraries().stream().map(BookingLookupResponse::toItinerarySummary).toList(),
        view.passengers().stream().map(BookingLookupResponse::toPassengerSummary).toList(),
        new Actions(
            view.actions().canSelectSeat(),
            view.actions().canBuyAncillaries(),
            view.actions().canCheckIn(),
            view.actions().canChangeFlight(),
            view.actions().canCancel()));
  }

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

  private static BookingSummary toBookingSummary(BookingLookupView.BookingSummary view) {
    return new BookingSummary(
        view.bookingId(),
        view.pnrCode(),
        view.status(),
        view.statusLabel(),
        view.totalAmount(),
        view.currency(),
        view.createdAt(),
        view.expiresAt());
  }

  private static PriceBreakdown toPriceBreakdown(BookingLookupView.PriceBreakdown view) {
    return new PriceBreakdown(
        view.baseFare(),
        view.discount(),
        view.tax(),
        view.serviceFee(),
        view.seatFee(),
        view.total(),
        view.currency());
  }

  private static ItinerarySummary toItinerarySummary(BookingLookupView.ItinerarySummary view) {
    return new ItinerarySummary(
        view.flightId(),
        view.flightNumber(),
        view.departureTime(),
        view.arrivalTime(),
        view.departureAirportCode(),
        view.departureCity(),
        view.departureTerminal(),
        view.arrivalAirportCode(),
        view.arrivalCity(),
        view.arrivalTerminal(),
        view.flightStatus(),
        view.fareName(),
        view.cabinClass(),
        toFareBenefits(view.fareBenefits()));
  }

  private static FareBenefits toFareBenefits(BookingLookupView.FareBenefits view) {
    BookingLookupView.IncludedSeatCategory category = view.includedSeatCategory();
    IncludedSeatCategory includedCategory =
        category == null
            ? null
            : new IncludedSeatCategory(category.id(), category.code(), category.name());
    return new FareBenefits(
        view.fareClassCode(),
        view.fareClassName(),
        view.cabinClass(),
        view.baggageCheckinKg(),
        view.baggageHandKg(),
        view.changeFee(),
        view.refundFee(),
        view.refundable(),
        view.changeable(),
        view.priorityCheckin(),
        view.priorityBoarding(),
        view.loungeAccess(),
        view.seatSelection(),
        view.seatSelectionPolicy(),
        view.mileageRate(),
        view.mealType(),
        view.wifiIncluded(),
        view.fastTrackSecurity(),
        includedCategory);
  }

  private static PassengerSummary toPassengerSummary(BookingLookupView.PassengerSummary view) {
    return new PassengerSummary(
        view.passengerId(),
        view.firstName(),
        view.lastName(),
        view.passengerType(),
        view.gender(),
        view.dateOfBirth(),
        new PassengerSelections(
            view.selections().seats().stream()
                .map(BookingLookupResponse::toSeatAssignmentDetail)
                .toList(),
            view.selections().services().stream()
                .map(BookingLookupResponse::toServiceSelectionDetail)
                .toList()));
  }

  private static SeatAssignmentDetail toSeatAssignmentDetail(
      BookingLookupView.SeatAssignmentDetail view) {
    return new SeatAssignmentDetail(
        view.flightId(),
        view.seatId(),
        view.seatNumber(),
        view.status(),
        view.surchargeAmount(),
        view.currency());
  }

  private static ServiceSelectionDetail toServiceSelectionDetail(
      BookingLookupView.ServiceSelectionDetail view) {
    return new ServiceSelectionDetail(
        view.serviceId(),
        view.serviceType(),
        view.serviceName(),
        view.optionId(),
        view.optionName(),
        view.quantity(),
        view.price());
  }
}
