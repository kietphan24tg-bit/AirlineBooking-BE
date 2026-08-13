package com.example.moduleweb.controller.booking.dto;

import com.example.moduleweb.application.booking.UserTripHistoryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record TripHistoryResponse(
    List<TripHistoryItemResponse> items, TripHistoryPageInfoResponse pageInfo) {

  public static TripHistoryResponse from(UserTripHistoryService.UserTripHistoryResult result) {
    return new TripHistoryResponse(
        result.items().stream().map(TripHistoryItemResponse::from).toList(),
        TripHistoryPageInfoResponse.from(result.pageInfo()));
  }

  public record TripHistoryItemResponse(
      Long bookingId,
      Long flightId,
      String pnrCode,
      String tripStatus,
      String tripStatusLabel,
      String paymentStatus,
      String paymentStatusLabel,
      String flightNumber,
      String cabinClass,
      String departureAirportCode,
      String departureCity,
      String arrivalAirportCode,
      String arrivalCity,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime,
      LocalDate flightDate,
      int passengerCount,
      BigDecimal totalAmount,
      String currency,
      TripActionsResponse actions) {

    static TripHistoryItemResponse from(UserTripHistoryService.TripHistoryItemView view) {
      return new TripHistoryItemResponse(
          view.bookingId(),
          view.flightId(),
          view.pnrCode(),
          view.tripStatus().name(),
          view.tripStatusLabel(),
          view.paymentStatus(),
          view.paymentStatusLabel(),
          view.flightNumber(),
          view.cabinClass(),
          view.departureAirportCode(),
          view.departureCity(),
          view.arrivalAirportCode(),
          view.arrivalCity(),
          view.departureTime(),
          view.arrivalTime(),
          view.flightDate(),
          view.passengerCount(),
          view.totalAmount(),
          view.currency(),
          TripActionsResponse.from(view.actions()));
    }
  }

  public record TripActionsResponse(
      boolean canManageBooking, boolean canCheckIn, boolean canViewDetail) {

    static TripActionsResponse from(UserTripHistoryService.TripActionsView view) {
      return new TripActionsResponse(
          view.canManageBooking(), view.canCheckIn(), view.canViewDetail());
    }
  }

  public record TripHistoryPageInfoResponse(String nextCursor, boolean hasMore, int limit) {
    static TripHistoryPageInfoResponse from(UserTripHistoryService.PageInfo pageInfo) {
      return new TripHistoryPageInfoResponse(
          pageInfo.nextCursor(), pageInfo.hasMore(), pageInfo.limit());
    }
  }
}
