package com.example.modulebooking.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.moduleflight.application.SeatSelectionPolicy;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.SeatCategoryEntity;
import com.example.moduleflight.entity.SeatTypeEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingSeatAssignmentService {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
  private static final String VND = "VND";
  private static final List<String> ACTIVE_ASSIGNMENT_STATUSES = List.of("RESERVED", "BOOKED");
  private static final List<String> ACTIVE_HOLD_STATUSES = List.of("HELD", "CONFIRMED");

  private final BookingRepository bookingRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final PassengerRepository passengerRepository;
  private final FlightSeatRepository flightSeatRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final BookingPaymentStatusPort bookingPaymentStatusPort;
  private final BookingTotalRecalculationService bookingTotalRecalculationService;

  public BookingSeatAssignmentService(
      BookingRepository bookingRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerRepository passengerRepository,
      FlightSeatRepository flightSeatRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      BookingPaymentStatusPort bookingPaymentStatusPort,
      BookingTotalRecalculationService bookingTotalRecalculationService) {
    this.bookingRepository = bookingRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.passengerRepository = passengerRepository;
    this.flightSeatRepository = flightSeatRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.bookingPaymentStatusPort = bookingPaymentStatusPort;
    this.bookingTotalRecalculationService = bookingTotalRecalculationService;
  }

  @Transactional
  public SeatAssignmentView assignSeats(
      Long userId, Long bookingId, SeatAssignmentCommand command) {
    BookingEntity booking = requireBooking(userId, bookingId);
    validateBookingAssignable(booking);
    BigDecimal fareTotal =
        "PENDING_PAYMENT".equals(booking.getStatus())
            ? bookingTotalRecalculationService.captureFareTotal(booking)
            : null;

    List<SeatAssignmentCommand.AssignmentCommand> assignments = command.assignments();
    validateNoDuplicates(assignments);
    List<Long> requestedSeatIds =
        assignments.stream().map(SeatAssignmentCommand.AssignmentCommand::seatId).toList();
    List<FlightSeatEntity> seats = flightSeatRepository.findAllById(requestedSeatIds);
    if (seats.size() != requestedSeatIds.size()) {
      throw new AppException("SEAT_NOT_FOUND", "One or more seats were not found", 404);
    }
    Long flightId = resolveSingleFlightId(seats);
    BookingFareHoldEntity hold = requireActiveHold(bookingId, flightId);
    long existingAssignments =
        passengerSeatAssignmentRepository.countByBookingIdAndFlightIdAndStatusIn(
            bookingId, flightId, ACTIVE_ASSIGNMENT_STATUSES);
    if (existingAssignments + assignments.size() > hold.getQuantity()) {
      throw new AppException(
          "SEAT_ASSIGNMENT_LIMIT_EXCEEDED", "Seat assignments exceed held seat quantity", 409);
    }

    Set<Long> allowedSeatIds =
        flightSeatRepository.findAllowedSeatIdsForFare(
            hold.getFlightFare().getFlight().getId(), hold.getFlightFare().getFareClass().getId());
    FareClassEntity fareClass = hold.getFlightFare().getFareClass();
    SeatSelectionPolicy selectionPolicy =
        SeatSelectionPolicy.fromValue(fareClass.getSeatSelectionPolicy());
    if (!selectionPolicy.allowsAdvanceSelection()) {
      throw new AppException(
          "SEAT_NOT_ALLOWED", "Seat selection is not allowed for selected fare", 409);
    }
    for (FlightSeatEntity seat : seats) {
      if (!hold.getFlightFare().getFlight().getId().equals(seat.getFlight().getId())) {
        throw new AppException("SEAT_NOT_ALLOWED", "Seat does not belong to booking flight", 409);
      }
      if (!allowedSeatIds.contains(seat.getId())) {
        throw new AppException("SEAT_NOT_ALLOWED", "Seat is not allowed for selected fare", 409);
      }
      if (!"AVAILABLE".equals(seat.getStatus())) {
        throw new AppException("SEAT_UNAVAILABLE", "Seat is no longer available", 409);
      }
      if (passengerSeatAssignmentRepository.existsByFlightSeatIdAndStatusIn(
          seat.getId(), ACTIVE_ASSIGNMENT_STATUSES)) {
        throw new AppException("SEAT_UNAVAILABLE", "Seat is already assigned", 409);
      }
    }
    Map<Long, BigDecimal> surchargeBySeatId =
        calculateSeatSurcharges(seats, selectionPolicy, fareClass.getIncludedSeatCategory());
    BigDecimal requestedSurchargeTotal =
        surchargeBySeatId.values().stream()
            .reduce(ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    validateRequestedSeatSurcharge(booking, requestedSurchargeTotal);

    List<Long> passengerIds =
        assignments.stream().map(SeatAssignmentCommand.AssignmentCommand::passengerId).toList();
    List<PassengerEntity> passengers =
        passengerIds.stream()
            .map(
                passengerId ->
                    passengerRepository
                        .findByIdAndBookingId(passengerId, bookingId)
                        .orElseThrow(
                            () ->
                                new AppException(
                                    "PASSENGER_NOT_FOUND", "Passenger not found", 404)))
            .toList();
    for (PassengerEntity passenger : passengers) {
      if ("INFANT".equals(passenger.getPassengerType())) {
        throw new AppException(
            "SEAT_NOT_ALLOWED", "INFANT passengers cannot be assigned seats", 409);
      }
      if (passengerSeatAssignmentRepository.existsByPassengerIdAndFlightIdAndStatusIn(
          passenger.getId(), flightId, ACTIVE_ASSIGNMENT_STATUSES)) {
        throw new AppException(
            "PASSENGER_ALREADY_ASSIGNED", "Passenger already has an assigned seat", 409);
      }
    }

    String assignmentStatus = "CONFIRMED".equals(booking.getStatus()) ? "BOOKED" : "RESERVED";
    int updatedSeats =
        "BOOKED".equals(assignmentStatus)
            ? flightSeatRepository.bookSelectedForBooking(bookingId, requestedSeatIds)
            : flightSeatRepository.reserveSelectedForBooking(
                bookingId, requestedSeatIds, booking.getPnrExpiresAt());
    if (updatedSeats != requestedSeatIds.size()) {
      throw new AppException("SEAT_UNAVAILABLE", "Seat is no longer available", 409);
    }

    OffsetDateTime now = OffsetDateTime.now();
    List<PassengerSeatAssignmentEntity> entities =
        assignments.stream()
            .map(
                assignment -> {
                  PassengerEntity passenger =
                      passengers.stream()
                          .filter(p -> p.getId().equals(assignment.passengerId()))
                          .findFirst()
                          .orElseThrow();
                  FlightSeatEntity seat =
                      seats.stream()
                          .filter(s -> s.getId().equals(assignment.seatId()))
                          .findFirst()
                          .orElseThrow();
                  PassengerSeatAssignmentEntity entity = new PassengerSeatAssignmentEntity();
                  entity.setBooking(booking);
                  entity.setPassenger(passenger);
                  entity.setFlightSeat(seat);
                  entity.setStatus(assignmentStatus);
                  entity.setSurchargeAmount(surchargeBySeatId.getOrDefault(seat.getId(), ZERO));
                  entity.setCurrency(booking.getCurrency());
                  entity.setCreatedAt(now);
                  entity.setUpdatedAt(now);
                  return entity;
                })
            .toList();
    passengerSeatAssignmentRepository.saveAll(entities);

    return buildActiveSeatAssignmentResponse(booking, bookingId, fareTotal);
  }

  @Transactional
  public SeatAssignmentView unassignSeats(
      Long userId, Long bookingId, SeatAssignmentCommand command) {
    BookingEntity booking = requireBooking(userId, bookingId);
    validateBookingAssignable(booking);
    BigDecimal fareTotal =
        "PENDING_PAYMENT".equals(booking.getStatus())
            ? bookingTotalRecalculationService.captureFareTotal(booking)
            : null;

    List<SeatAssignmentCommand.AssignmentCommand> toUnassign = command.assignments();
    validateNoDuplicates(toUnassign);
    OffsetDateTime now = OffsetDateTime.now();
    List<PassengerSeatAssignmentEntity> toRelease = new ArrayList<>();
    for (SeatAssignmentCommand.AssignmentCommand assignment : toUnassign) {
      PassengerSeatAssignmentEntity psa =
          passengerSeatAssignmentRepository
              .findFirstByActiveAssignment(
                  bookingId,
                  assignment.passengerId(),
                  assignment.seatId(),
                  ACTIVE_ASSIGNMENT_STATUSES)
              .orElseThrow(
                  () ->
                      new AppException(
                          "SEAT_ASSIGNMENT_NOT_FOUND", "No active seat assignment to cancel", 404));
      psa.setStatus("RELEASED");
      psa.setUpdatedAt(now);
      toRelease.add(psa);
    }
    passengerSeatAssignmentRepository.saveAll(toRelease);

    List<Long> seatIds =
        toUnassign.stream().map(SeatAssignmentCommand.AssignmentCommand::seatId).toList();
    if (!seatIds.isEmpty()) {
      int released = flightSeatRepository.releaseSelectedForBooking(bookingId, seatIds);
      if (released != seatIds.size()) {
        throw new AppException("SEAT_RELEASE_FAILED", "Could not release one or more seats", 409);
      }
    }

    return buildActiveSeatAssignmentResponse(booking, bookingId, fareTotal);
  }

  @Transactional
  public SeatAssignmentView changeSeat(Long userId, Long bookingId, SeatChangeCommand command) {
    BookingEntity booking = requireBooking(userId, bookingId);
    validateBookingAssignable(booking);
    BigDecimal fareTotal =
        "PENDING_PAYMENT".equals(booking.getStatus())
            ? bookingTotalRecalculationService.captureFareTotal(booking)
            : null;

    if (command.toSeatId() != null && command.toSeatId().equals(command.fromSeatId())) {
      throw new AppException(
          "SEAT_CHANGE_NOOP", "Source and destination seats must be different", 409);
    }

    PassengerSeatAssignmentEntity currentAssignment =
        passengerSeatAssignmentRepository
            .findFirstByActiveAssignment(
                bookingId, command.passengerId(), command.fromSeatId(), ACTIVE_ASSIGNMENT_STATUSES)
            .orElseThrow(
                () ->
                    new AppException(
                        "SEAT_ASSIGNMENT_NOT_FOUND", "No active seat assignment to change", 404));
    if ("INFANT".equals(currentAssignment.getPassenger().getPassengerType())) {
      throw new AppException("SEAT_NOT_ALLOWED", "INFANT passengers cannot be assigned seats", 409);
    }

    OffsetDateTime now = OffsetDateTime.now();
    currentAssignment.setStatus("RELEASED");
    currentAssignment.setUpdatedAt(now);
    passengerSeatAssignmentRepository.saveAll(List.of(currentAssignment));

    int released =
        flightSeatRepository.releaseSelectedForBooking(bookingId, List.of(command.fromSeatId()));
    if (released != 1) {
      throw new AppException("SEAT_RELEASE_FAILED", "Could not release source seat", 409);
    }

    if (command.toSeatId() == null) {
      return buildActiveSeatAssignmentResponse(booking, bookingId, fareTotal);
    }

    FlightSeatEntity nextSeat =
        flightSeatRepository
            .findById(command.toSeatId())
            .orElseThrow(() -> new AppException("SEAT_NOT_FOUND", "Seat not found", 404));
    BookingFareHoldEntity hold = requireActiveHold(bookingId, nextSeat.getFlight().getId());
    FareClassEntity fareClass = hold.getFlightFare().getFareClass();
    SeatSelectionPolicy selectionPolicy =
        SeatSelectionPolicy.fromValue(fareClass.getSeatSelectionPolicy());
    if (!selectionPolicy.allowsAdvanceSelection()) {
      throw new AppException(
          "SEAT_NOT_ALLOWED", "Seat selection is not allowed for selected fare", 409);
    }
    validateRequestedSeat(hold, nextSeat, command.toSeatId());

    Map<Long, BigDecimal> surchargeBySeatId =
        calculateSeatSurcharges(
            List.of(nextSeat), selectionPolicy, fareClass.getIncludedSeatCategory());
    BigDecimal requestedSurchargeTotal = surchargeBySeatId.getOrDefault(nextSeat.getId(), ZERO);
    validateRequestedSeatSurcharge(booking, requestedSurchargeTotal);

    String assignmentStatus = "CONFIRMED".equals(booking.getStatus()) ? "BOOKED" : "RESERVED";
    int updatedSeats =
        "BOOKED".equals(assignmentStatus)
            ? flightSeatRepository.bookSelectedForBooking(bookingId, List.of(command.toSeatId()))
            : flightSeatRepository.reserveSelectedForBooking(
                bookingId, List.of(command.toSeatId()), booking.getPnrExpiresAt());
    if (updatedSeats != 1) {
      throw new AppException("SEAT_UNAVAILABLE", "Seat is no longer available", 409);
    }

    PassengerSeatAssignmentEntity replacement = new PassengerSeatAssignmentEntity();
    replacement.setBooking(booking);
    replacement.setPassenger(currentAssignment.getPassenger());
    replacement.setFlightSeat(nextSeat);
    replacement.setStatus(assignmentStatus);
    replacement.setSurchargeAmount(requestedSurchargeTotal);
    replacement.setCurrency(booking.getCurrency());
    replacement.setCreatedAt(now);
    replacement.setUpdatedAt(now);
    passengerSeatAssignmentRepository.save(replacement);

    return buildActiveSeatAssignmentResponse(booking, bookingId, fareTotal);
  }

  private BookingEntity requireBooking(Long userId, Long bookingId) {
    return userId == null
        ? bookingRepository
            .findById(bookingId)
            .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404))
        : bookingRepository
            .findByIdAndUserId(bookingId, userId)
            .orElseThrow(() -> new AppException("BOOKING_NOT_FOUND", "Booking not found", 404));
  }

  private void validateBookingAssignable(BookingEntity booking) {
    if (!"PENDING_PAYMENT".equals(booking.getStatus())
        && !"CONFIRMED".equals(booking.getStatus())) {
      throw new AppException(
          "BOOKING_NOT_ASSIGNABLE", "Booking is not eligible for seat assignment", 409);
    }
    if ("PENDING_PAYMENT".equals(booking.getStatus())
        && booking.getPnrExpiresAt() != null
        && OffsetDateTime.now().isAfter(booking.getPnrExpiresAt())) {
      throw new AppException("BOOKING_EXPIRED", "Booking payment window has expired", 410);
    }
    if (bookingPaymentStatusPort.hasPendingBookingPayment(booking.getId())) {
      throw new AppException(
          "BOOKING_PAYMENT_ALREADY_STARTED",
          "Booking seats cannot be changed after payment has started",
          409);
    }
  }

  private Map<Long, BigDecimal> calculateSeatSurcharges(
      List<FlightSeatEntity> seats,
      SeatSelectionPolicy selectionPolicy,
      SeatCategoryEntity includedSeatCategory) {
    Map<Long, BigDecimal> surchargeBySeatId = new HashMap<>();
    for (FlightSeatEntity seat : seats) {
      BigDecimal surcharge =
          isSeatIncluded(selectionPolicy, seat, includedSeatCategory)
              ? ZERO
              : surchargeAmount(seat.getSeatCategory()).add(surchargeAmount(seat.getSeatType()));
      surchargeBySeatId.put(seat.getId(), normalizeMoney(surcharge));
    }
    return surchargeBySeatId;
  }

  private boolean isSeatIncluded(
      SeatSelectionPolicy selectionPolicy,
      FlightSeatEntity seat,
      SeatCategoryEntity includedSeatCategory) {
    if (!selectionPolicy.isIncluded()
        || includedSeatCategory == null
        || seat.getSeatCategory() == null) {
      return false;
    }
    Integer includedRank = includedSeatCategory.getPricingRank();
    Integer seatRank = seat.getSeatCategory().getPricingRank();
    if (includedRank == null || seatRank == null) {
      return false;
    }
    return seatRank <= includedRank;
  }

  private BigDecimal surchargeAmount(SeatCategoryEntity seatCategory) {
    if (seatCategory == null || seatCategory.getSurchargeAmount() == null) {
      return ZERO;
    }
    return seatCategory.getSurchargeAmount();
  }

  private BigDecimal surchargeAmount(SeatTypeEntity seatType) {
    if (seatType == null || seatType.getSurchargeAmount() == null) {
      return ZERO;
    }
    return seatType.getSurchargeAmount();
  }

  private BigDecimal normalizeMoney(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }

  private BookingFareHoldEntity requireActiveHold(Long bookingId, Long flightId) {
    return bookingFareHoldRepository
        .findByBookingIdAndFlightIdAndStatusIn(bookingId, flightId, ACTIVE_HOLD_STATUSES)
        .orElseThrow(() -> new AppException("FARE_HOLD_NOT_FOUND", "Fare hold not found", 409));
  }

  private Long resolveSingleFlightId(List<FlightSeatEntity> seats) {
    Long flightId = null;
    for (FlightSeatEntity seat : seats) {
      Long seatFlightId = seat.getFlight().getId();
      if (flightId == null) {
        flightId = seatFlightId;
        continue;
      }
      if (!flightId.equals(seatFlightId)) {
        throw new AppException(
            "SEAT_NOT_ALLOWED", "All seats in one request must belong to the same flight", 409);
      }
    }
    if (flightId == null) {
      throw new AppException("SEAT_NOT_FOUND", "One or more seats were not found", 404);
    }
    return flightId;
  }

  private void validateRequestedSeat(
      BookingFareHoldEntity hold, FlightSeatEntity seat, Long seatId) {
    Set<Long> allowedSeatIds =
        flightSeatRepository.findAllowedSeatIdsForFare(
            hold.getFlightFare().getFlight().getId(), hold.getFlightFare().getFareClass().getId());
    if (!hold.getFlightFare().getFlight().getId().equals(seat.getFlight().getId())) {
      throw new AppException("SEAT_NOT_ALLOWED", "Seat does not belong to booking flight", 409);
    }
    if (!allowedSeatIds.contains(seatId)) {
      throw new AppException("SEAT_NOT_ALLOWED", "Seat is not allowed for selected fare", 409);
    }
    if (!"AVAILABLE".equals(seat.getStatus())) {
      throw new AppException("SEAT_UNAVAILABLE", "Seat is no longer available", 409);
    }
    if (passengerSeatAssignmentRepository.existsByFlightSeatIdAndStatusIn(
        seatId, ACTIVE_ASSIGNMENT_STATUSES)) {
      throw new AppException("SEAT_UNAVAILABLE", "Seat is already assigned", 409);
    }
  }

  private void validateRequestedSeatSurcharge(
      BookingEntity booking, BigDecimal requestedSurchargeTotal) {
    if (requestedSurchargeTotal.signum() > 0 && !VND.equalsIgnoreCase(booking.getCurrency())) {
      throw new AppException(
          "SEAT_SURCHARGE_CURRENCY_UNSUPPORTED",
          "Seat surcharge is only supported for VND bookings",
          409);
    }
    if ("CONFIRMED".equals(booking.getStatus()) && requestedSurchargeTotal.signum() > 0) {
      throw new AppException(
          "SEAT_PAYMENT_REQUIRED",
          "Paid seat selection is not supported after booking is confirmed",
          409);
    }
  }

  private SeatAssignmentView buildActiveSeatAssignmentResponse(
      BookingEntity booking, Long bookingId, BigDecimal fareTotal) {
    BigDecimal seatSurchargeTotal =
        normalizeMoney(passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(bookingId));
    if ("PENDING_PAYMENT".equals(booking.getStatus())) {
      BookingTotalRecalculationService.BookingTotalBreakdown totalBreakdown =
          bookingTotalRecalculationService.refreshTotal(booking, fareTotal);
      seatSurchargeTotal = totalBreakdown.seatSurchargeTotal();
    }

    List<PassengerSeatAssignmentEntity> remaining =
        passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(
            bookingId, ACTIVE_ASSIGNMENT_STATUSES);
    String displayStatus = remaining.isEmpty() ? "NONE" : remaining.get(0).getStatus();

    return new SeatAssignmentView(
        bookingId,
        displayStatus,
        seatSurchargeTotal,
        booking.getTotalAmount(),
        remaining.stream()
            .map(
                psa ->
                    new SeatAssignmentView.AssignmentView(
                        psa.getPassenger().getId(),
                        psa.getFlightSeat().getId(),
                        psa.getFlightSeat().getFlight().getId(),
                        psa.getFlightSeat().getSeatNumber(),
                        psa.getStatus(),
                        psa.getSurchargeAmount(),
                        psa.getCurrency()))
            .toList());
  }

  private void validateNoDuplicates(List<SeatAssignmentCommand.AssignmentCommand> assignments) {
    Set<Long> passengerIds = new HashSet<>();
    Set<Long> seatIds = new HashSet<>();
    for (SeatAssignmentCommand.AssignmentCommand assignment : assignments) {
      if (!passengerIds.add(assignment.passengerId())) {
        throw new AppException("DUPLICATE_PASSENGER", "Passenger is assigned more than once", 400);
      }
      if (!seatIds.add(assignment.seatId())) {
        throw new AppException("DUPLICATE_SEAT", "Seat is assigned more than once", 400);
      }
    }
  }
}
