package com.example.moduleweb.application.booking;

import com.example.modulebooking.application.SeatAssignmentCommand;
import com.example.modulebooking.application.SeatAssignmentView;
import com.example.modulebooking.application.SeatChangeCommand;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulepayment.repository.PaymentRepository;
import com.example.moduleweb.controller.booking.dto.SeatAssignmentRequest;
import com.example.moduleweb.controller.booking.dto.SeatAssignmentResponse;
import com.example.moduleweb.controller.booking.dto.SeatChangeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("webBookingSeatAssignmentService")
public class BookingSeatAssignmentService {

  private final com.example.modulebooking.application.BookingSeatAssignmentService delegate;

  @Autowired
  public BookingSeatAssignmentService(
      com.example.modulebooking.application.BookingSeatAssignmentService delegate) {
    this.delegate = delegate;
  }

  public BookingSeatAssignmentService(
      BookingRepository bookingRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerRepository passengerRepository,
      FlightSeatRepository flightSeatRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      PaymentRepository paymentRepository,
      BookingTotalRecalculationService bookingTotalRecalculationService) {
    this(
        new com.example.modulebooking.application.BookingSeatAssignmentService(
            bookingRepository,
            bookingFareHoldRepository,
            passengerRepository,
            flightSeatRepository,
            passengerSeatAssignmentRepository,
            bookingId -> paymentRepository.existsByBookingIdAndPaymentStatus(bookingId, "PENDING"),
            bookingTotalRecalculationService.toModuleBookingService()));
  }

  public SeatAssignmentResponse assignSeats(
      Long userId, Long bookingId, SeatAssignmentRequest request) {
    return toResponse(
        delegate.assignSeats(
            userId,
            bookingId,
            new SeatAssignmentCommand(
                request.assignments().stream()
                    .map(
                        item ->
                            new SeatAssignmentCommand.AssignmentCommand(
                                item.passengerId(), item.seatId()))
                    .toList())));
  }

  public SeatAssignmentResponse unassignSeats(
      Long userId, Long bookingId, SeatAssignmentRequest request) {
    return toResponse(
        delegate.unassignSeats(
            userId,
            bookingId,
            new SeatAssignmentCommand(
                request.assignments().stream()
                    .map(
                        item ->
                            new SeatAssignmentCommand.AssignmentCommand(
                                item.passengerId(), item.seatId()))
                    .toList())));
  }

  public SeatAssignmentResponse changeSeat(Long userId, Long bookingId, SeatChangeRequest request) {
    return toResponse(
        delegate.changeSeat(
            userId,
            bookingId,
            new SeatChangeCommand(
                request.passengerId(), request.fromSeatId(), request.toSeatId())));
  }

  private SeatAssignmentResponse toResponse(SeatAssignmentView view) {
    return new SeatAssignmentResponse(
        view.bookingId(),
        view.status(),
        view.seatSurchargeTotal(),
        view.totalAmount(),
        view.assignments().stream()
            .map(
                item ->
                    new SeatAssignmentResponse.AssignmentResponse(
                        item.passengerId(),
                        item.seatId(),
                        item.flightId(),
                        item.seatNumber(),
                        item.status(),
                        item.surchargeAmount(),
                        item.currency()))
            .toList());
  }
}
