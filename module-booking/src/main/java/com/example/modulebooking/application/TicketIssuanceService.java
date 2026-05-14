package com.example.modulebooking.application;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.exception.BookingNotFoundException;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketIssuanceService {

  private static final String BOOKING_STATUS_CONFIRMED = "CONFIRMED";
  private static final String TICKET_STATUS_ISSUED = "ISSUED";

  private final BookingRepository bookingRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final PassengerRepository passengerRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final TicketRepository ticketRepository;

  public TicketIssuanceService(
      BookingRepository bookingRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerRepository passengerRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      TicketRepository ticketRepository) {
    this.bookingRepository = bookingRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.passengerRepository = passengerRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.ticketRepository = ticketRepository;
  }

  @Transactional
  public BookingTicketsView issueTicketsForConfirmedBooking(Long bookingId) {
    BookingEntity booking =
        bookingRepository
            .findById(bookingId)
            .orElseThrow(() -> new BookingNotFoundException(bookingId));
    requireConfirmed(booking);
    issueMissingTickets(booking);
    return getTicketsForBooking(null, bookingId);
  }

  @Transactional
  public BookingTicketsView issueTicketsForBooking(Long userId, Long bookingId) {
    BookingEntity booking =
        userId == null
            ? bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId))
            : bookingRepository
                .findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    requireConfirmed(booking);
    issueMissingTickets(booking);
    return getTicketsForBooking(userId, bookingId);
  }

  @Transactional(readOnly = true)
  public BookingTicketsView getTicketsForBooking(Long userId, Long bookingId) {
    BookingEntity booking =
        userId == null
            ? bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId))
            : bookingRepository
                .findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    requireConfirmed(booking);

    List<TicketEntity> tickets = ticketRepository.findByBookingIdWithDetails(bookingId);
    if (tickets.isEmpty()) {
      throw new AppException(
          "TICKETS_NOT_ISSUED",
          "Tickets have not been issued for this booking yet",
          409,
          Map.of("bookingId", bookingId));
    }
    return toView(booking, tickets);
  }

  private void issueMissingTickets(BookingEntity booking) {
    List<BookingFareHoldEntity> fareHolds = requireConfirmedFareHolds(booking.getId());
    List<PassengerEntity> passengers =
        passengerRepository.findByBookingIdOrderById(booking.getId());
    Map<String, FlightSeatEntity> bookedSeats = bookedSeatByPassengerAndFlight(booking.getId());
    Set<String> ticketedPassengerFlightKeys =
        ticketRepository.findByBookingIdWithDetails(booking.getId()).stream()
            .map(ticket -> ticketKey(ticket.getPassenger().getId(), ticket.getFlight().getId()))
            .collect(Collectors.toSet());

    OffsetDateTime now = OffsetDateTime.now();
    boolean multiSegment = fareHolds.size() > 1;
    List<TicketEntity> missingTickets = new java.util.ArrayList<>();
    for (BookingFareHoldEntity fareHold : fareHolds) {
      FlightFareEntity fare = fareHold.getFlightFare();
      FlightEntity flight = fare.getFlight();
      FareClassEntity fareClass = fare.getFareClass();
      for (PassengerEntity passenger : passengers) {
        String key = ticketKey(passenger.getId(), flight.getId());
        if (ticketedPassengerFlightKeys.contains(key)) {
          continue;
        }
        TicketEntity ticket = new TicketEntity();
        ticket.setBooking(booking);
        ticket.setFlightFare(fare);
        ticket.setPassenger(passenger);
        ticket.setFlight(flight);
        ticket.setFlightSeat(bookedSeats.get(key));
        ticket.setFareClass(fareClass);
        ticket.setTicketNumber(ticketNumber(booking, passenger, flight, multiSegment));
        ticket.setStatus(TICKET_STATUS_ISSUED);
        ticket.setIssuedAt(now);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        missingTickets.add(ticket);
      }
    }
    if (!missingTickets.isEmpty()) {
      ticketRepository.saveAll(missingTickets);
    }
  }

  private List<BookingFareHoldEntity> requireConfirmedFareHolds(Long bookingId) {
    List<BookingFareHoldEntity> confirmed =
        bookingFareHoldRepository.findByBookingIdOrderByIdAsc(bookingId).stream()
            .filter(item -> "CONFIRMED".equalsIgnoreCase(item.getStatus()))
            .sorted(
                Comparator.comparing(
                        (BookingFareHoldEntity item) ->
                            item.getFlightFare().getFlight().getDepartureTime(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(item -> item.getFlightFare().getFlight().getId()))
            .toList();
    if (!confirmed.isEmpty()) {
      return confirmed;
    }
    throw new AppException(
        "BOOKING_FARE_NOT_CONFIRMED",
        "Booking fare is not confirmed",
        409,
        Map.of("bookingId", bookingId));
  }

  private Map<String, FlightSeatEntity> bookedSeatByPassengerAndFlight(Long bookingId) {
    return passengerSeatAssignmentRepository
        .findByBookingIdAndStatusIn(bookingId, List.of("BOOKED"))
        .stream()
        .collect(
            Collectors.toMap(
                assignment ->
                    ticketKey(
                        assignment.getPassenger().getId(),
                        assignment.getFlightSeat().getFlight().getId()),
                PassengerSeatAssignmentEntity::getFlightSeat,
                (left, right) -> left,
                LinkedHashMap::new));
  }

  private void requireConfirmed(BookingEntity booking) {
    if (!BOOKING_STATUS_CONFIRMED.equalsIgnoreCase(booking.getStatus())) {
      throw new AppException(
          "BOOKING_NOT_CONFIRMED",
          "Booking is not confirmed",
          409,
          Map.of("bookingId", booking.getId(), "status", booking.getStatus()));
    }
  }

  private String ticketNumber(
      BookingEntity booking, PassengerEntity passenger, FlightEntity flight, boolean multiSegment) {
    if (!multiSegment) {
      return booking.getPnrCode() + "-" + passenger.getId();
    }
    return booking.getPnrCode() + "-" + passenger.getId() + "-" + flight.getId();
  }

  private String ticketKey(Long passengerId, Long flightId) {
    return passengerId + ":" + flightId;
  }

  private BookingTicketsView toView(BookingEntity booking, List<TicketEntity> tickets) {
    return new BookingTicketsView(
        booking.getId(), booking.getPnrCode(), tickets.stream().map(this::ticketView).toList());
  }

  private BookingTicketsView.TicketView ticketView(TicketEntity ticket) {
    PassengerEntity passenger = ticket.getPassenger();
    FlightSeatEntity seat = ticket.getFlightSeat();
    return new BookingTicketsView.TicketView(
        ticket.getId(),
        ticket.getTicketNumber(),
        ticket.getStatus(),
        ticket.getIssuedAt(),
        new BookingTicketsView.PassengerView(
            passenger.getId(),
            passenger.getFirstName(),
            passenger.getLastName(),
            passenger.getPassengerType()),
        seat == null ? null : new BookingTicketsView.SeatView(seat.getId(), seat.getSeatNumber()));
  }
}
