package com.example.appbootstrap.controller.booking.dto;

import com.example.appbootstrap.application.booking.UserTripDetailService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record TripDetailResponse(
    BookingResponse booking,
    SegmentResponse segment,
    List<PassengerResponse> passengers,
    PaymentResponse payment,
    ActionsResponse actions) {

  public static TripDetailResponse from(UserTripDetailService.TripDetailView view) {
    return new TripDetailResponse(
        BookingResponse.from(view.booking()),
        SegmentResponse.from(view.segment()),
        view.passengers().stream().map(PassengerResponse::from).toList(),
        PaymentResponse.from(view.payment()),
        ActionsResponse.from(view.actions()));
  }

  public record BookingResponse(
      Long bookingId,
      String pnrCode,
      String bookingStatus,
      String bookingStatusLabel,
      String tripStatus,
      String tripStatusLabel,
      BigDecimal totalAmount,
      String currency,
      OffsetDateTime createdAt) {
    static BookingResponse from(UserTripDetailService.BookingSummaryView view) {
      return new BookingResponse(
          view.bookingId(),
          view.pnrCode(),
          view.bookingStatus(),
          view.bookingStatusLabel(),
          view.tripStatus(),
          view.tripStatusLabel(),
          view.totalAmount(),
          view.currency(),
          view.createdAt());
    }
  }

  public record SegmentResponse(
      Long flightId,
      String flightNumber,
      LocalDate flightDate,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime,
      int durationMinutes,
      String departureAirportCode,
      String departureAirportName,
      String departureCity,
      String departureTerminal,
      String arrivalAirportCode,
      String arrivalAirportName,
      String arrivalCity,
      String arrivalTerminal,
      String fareClassCode,
      String fareClassName,
      String cabinClass,
      Integer checkedBaggageKg,
      Integer carryOnBaggageKg) {
    static SegmentResponse from(UserTripDetailService.SegmentView view) {
      return new SegmentResponse(
          view.flightId(),
          view.flightNumber(),
          view.flightDate(),
          view.departureTime(),
          view.arrivalTime(),
          view.durationMinutes(),
          view.departureAirportCode(),
          view.departureAirportName(),
          view.departureCity(),
          view.departureTerminal(),
          view.arrivalAirportCode(),
          view.arrivalAirportName(),
          view.arrivalCity(),
          view.arrivalTerminal(),
          view.fareClassCode(),
          view.fareClassName(),
          view.cabinClass(),
          view.checkedBaggageKg(),
          view.carryOnBaggageKg());
    }
  }

  public record PassengerResponse(
      Long passengerId,
      String firstName,
      String lastName,
      String fullName,
      String passengerType,
      TicketResponse ticket,
      SeatResponse seat) {
    static PassengerResponse from(UserTripDetailService.PassengerView view) {
      return new PassengerResponse(
          view.passengerId(),
          view.firstName(),
          view.lastName(),
          view.fullName(),
          view.passengerType(),
          view.ticket() == null ? null : TicketResponse.from(view.ticket()),
          view.seat() == null ? null : SeatResponse.from(view.seat()));
    }
  }

  public record TicketResponse(Long ticketId, String ticketNumber, String status, OffsetDateTime issuedAt) {
    static TicketResponse from(UserTripDetailService.TicketView view) {
      return new TicketResponse(view.ticketId(), view.ticketNumber(), view.status(), view.issuedAt());
    }
  }

  public record SeatResponse(
      Long seatId, String seatNumber, String status, BigDecimal surchargeAmount, String currency) {
    static SeatResponse from(UserTripDetailService.SeatView view) {
      return new SeatResponse(
          view.seatId(), view.seatNumber(), view.status(), view.surchargeAmount(), view.currency());
    }
  }

  public record PaymentResponse(
      String paymentStatus,
      String paymentStatusLabel,
      String paymentMethod,
      String transactionId,
      BigDecimal amount,
      String currency) {
    static PaymentResponse from(UserTripDetailService.PaymentView view) {
      return new PaymentResponse(
          view.paymentStatus(),
          view.paymentStatusLabel(),
          view.paymentMethod(),
          view.transactionId(),
          view.amount(),
          view.currency());
    }
  }

  public record ActionsResponse(
      boolean canManageBooking, boolean canViewETicket, boolean canDownloadInvoice) {
    static ActionsResponse from(UserTripDetailService.ActionsView view) {
      return new ActionsResponse(
          view.canManageBooking(), view.canViewETicket(), view.canDownloadInvoice());
    }
  }
}
