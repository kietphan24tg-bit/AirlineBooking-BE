package com.example.appbootstrap.controller.booking.dto;

import com.example.appbootstrap.application.booking.BookingManageOptionsService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingManageOptionsResponse(
    Long bookingId,
    String bookingStatus,
    String currency,
    List<PassengerResponse> passengers,
    List<ItineraryResponse> itineraries,
    List<SeatAssignmentResponse> seatAssignments,
    List<ServiceSelectionResponse> serviceSelections) {

  public static BookingManageOptionsResponse from(
      BookingManageOptionsService.BookingManageOptionsView view) {
    return new BookingManageOptionsResponse(
        view.bookingId(),
        view.bookingStatus(),
        view.currency(),
        view.passengers().stream().map(PassengerResponse::from).toList(),
        view.itineraries().stream().map(ItineraryResponse::from).toList(),
        view.seatAssignments().stream().map(SeatAssignmentResponse::from).toList(),
        view.serviceSelections().stream().map(ServiceSelectionResponse::from).toList());
  }

  public record PassengerResponse(
      Long passengerId,
      String firstName,
      String lastName,
      String passengerType,
      List<PassengerSegmentCheckInResponse> segments) {
    static PassengerResponse from(BookingManageOptionsService.PassengerView view) {
      return new PassengerResponse(
          view.passengerId(),
          view.firstName(),
          view.lastName(),
          view.passengerType(),
          view.segments().stream().map(PassengerSegmentCheckInResponse::from).toList());
    }
  }

  public record ItineraryResponse(
      Long flightId,
      Long fareId,
      String flightNumber,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime,
      String departureAirportCode,
      String departureCity,
      String departureTerminal,
      String arrivalAirportCode,
      String arrivalCity,
      String arrivalTerminal,
      String fareName,
      String cabinClass,
      Integer baggageHandKg,
      Integer baggageCheckinKg,
      String journeyType,
      CheckInWindowResponse checkInWindow,
      String segmentStatus) {
    static ItineraryResponse from(BookingManageOptionsService.ItineraryView view) {
      return new ItineraryResponse(
          view.flightId(),
          view.fareId(),
          view.flightNumber(),
          view.departureTime(),
          view.arrivalTime(),
          view.departureAirportCode(),
          view.departureCity(),
          view.departureTerminal(),
          view.arrivalAirportCode(),
          view.arrivalCity(),
          view.arrivalTerminal(),
          view.fareName(),
          view.cabinClass(),
          view.baggageHandKg(),
          view.baggageCheckinKg(),
          view.journeyType(),
          CheckInWindowResponse.from(view.checkInWindow()),
          view.segmentStatus());
    }
  }

  public record SeatAssignmentResponse(
      Long passengerId,
      Long flightId,
      Long seatId,
      String seatNumber,
      String status,
      BigDecimal surchargeAmount,
      String currency) {
    static SeatAssignmentResponse from(BookingManageOptionsService.SeatAssignmentView view) {
      return new SeatAssignmentResponse(
          view.passengerId(),
          view.flightId(),
          view.seatId(),
          view.seatNumber(),
          view.status(),
          view.surchargeAmount(),
          view.currency());
    }
  }

  public record ServiceSelectionResponse(
      Long passengerId,
      Long serviceId,
      String serviceType,
      String serviceName,
      Long optionId,
      String optionName,
      Integer quantity,
      BigDecimal price) {
    static ServiceSelectionResponse from(BookingManageOptionsService.ServiceSelectionView view) {
      return new ServiceSelectionResponse(
          view.passengerId(),
          view.serviceId(),
          view.serviceType(),
          view.serviceName(),
          view.optionId(),
          view.optionName(),
          view.quantity(),
          view.price());
    }
  }

  public record PassengerSegmentCheckInResponse(
      Long flightId,
      boolean eligible,
      String checkInStatus,
      boolean seatAssigned,
      String seatNumber,
      List<String> warnings) {
    static PassengerSegmentCheckInResponse from(
        BookingManageOptionsService.PassengerSegmentCheckInView view) {
      return new PassengerSegmentCheckInResponse(
          view.flightId(),
          view.eligible(),
          view.checkInStatus(),
          view.seatAssigned(),
          view.seatNumber(),
          view.warnings());
    }
  }

  public record CheckInWindowResponse(
      String status, OffsetDateTime opensAt, OffsetDateTime closesAt, String message) {
    static CheckInWindowResponse from(BookingManageOptionsService.CheckInWindowView view) {
      return new CheckInWindowResponse(
          view.status(), view.opensAt(), view.closesAt(), view.message());
    }
  }
}
