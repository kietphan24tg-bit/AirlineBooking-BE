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
      Long passengerId, String firstName, String lastName, String passengerType) {
    static PassengerResponse from(BookingManageOptionsService.PassengerView view) {
      return new PassengerResponse(
          view.passengerId(), view.firstName(), view.lastName(), view.passengerType());
    }
  }

  public record ItineraryResponse(
      Long flightId,
      Long fareId,
      String flightNumber,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime,
      String departureAirportCode,
      String arrivalAirportCode) {
    static ItineraryResponse from(BookingManageOptionsService.ItineraryView view) {
      return new ItineraryResponse(
          view.flightId(),
          view.fareId(),
          view.flightNumber(),
          view.departureTime(),
          view.arrivalTime(),
          view.departureAirportCode(),
          view.arrivalAirportCode());
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
}
