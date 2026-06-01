package com.example.appbootstrap.application.booking;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
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
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserTripDetailService {

  private static final List<String> ACTIVE_ASSIGNMENT_STATUSES = List.of("RESERVED", "BOOKED");

  private final BookingRepository bookingRepository;
  private final BookingFareHoldRepository bookingFareHoldRepository;
  private final PassengerRepository passengerRepository;
  private final PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  private final TicketRepository ticketRepository;
  private final PaymentRepository paymentRepository;

  public UserTripDetailService(
      BookingRepository bookingRepository,
      BookingFareHoldRepository bookingFareHoldRepository,
      PassengerRepository passengerRepository,
      PassengerSeatAssignmentRepository passengerSeatAssignmentRepository,
      TicketRepository ticketRepository,
      PaymentRepository paymentRepository) {
    this.bookingRepository = bookingRepository;
    this.bookingFareHoldRepository = bookingFareHoldRepository;
    this.passengerRepository = passengerRepository;
    this.passengerSeatAssignmentRepository = passengerSeatAssignmentRepository;
    this.ticketRepository = ticketRepository;
    this.paymentRepository = paymentRepository;
  }

  @Transactional(readOnly = true)
  public TripDetailView getTripDetail(Long userId, Long bookingId, Long flightId) {
    BookingEntity booking =
        bookingRepository
            .findByIdAndUserId(bookingId, userId)
            .orElseThrow(() -> new BookingNotFoundException(bookingId));

    BookingFareHoldEntity hold =
        bookingFareHoldRepository
            .findByBookingIdAndFlightIdAndStatusIn(
                bookingId, flightId, List.of("HELD", "CONFIRMED", "RELEASED"))
            .orElseThrow(() -> new AppException("TRIP_NOT_FOUND", "Trip segment not found", 404));

    FlightFareEntity fare = hold.getFlightFare();
    FlightEntity flight = fare.getFlight();
    FareClassEntity fareClass = fare.getFareClass();

    List<PassengerEntity> passengers = passengerRepository.findByBookingIdOrderById(bookingId);
    List<PassengerSeatAssignmentEntity> seats =
        passengerSeatAssignmentRepository.findByBookingIdAndFlightIdAndStatusIn(
            bookingId, flightId, ACTIVE_ASSIGNMENT_STATUSES);
    List<TicketEntity> tickets =
        ticketRepository.findByBookingIdAndFlightIdWithDetails(bookingId, flightId);
    PaymentEntity payment =
        paymentRepository.findBookingLevelPaymentsByBookingIdOrderByLatest(bookingId).stream()
            .findFirst()
            .orElse(null);

    Map<Long, PassengerSeatAssignmentEntity> seatByPassengerId = new LinkedHashMap<>();
    for (PassengerSeatAssignmentEntity seat : seats) {
      seatByPassengerId.putIfAbsent(seat.getPassenger().getId(), seat);
    }
    Map<Long, TicketEntity> ticketByPassengerId = new LinkedHashMap<>();
    for (TicketEntity ticket : tickets) {
      ticketByPassengerId.putIfAbsent(ticket.getPassenger().getId(), ticket);
    }

    return new TripDetailView(
        new BookingSummaryView(
            booking.getId(),
            booking.getPnrCode(),
            booking.getStatus(),
            bookingStatusLabel(booking.getStatus()),
            classifyTripStatus(
                booking.getStatus(), hold.getStatus(), flight.getDepartureTime(), seats, tickets),
            tripStatusLabel(
                classifyTripStatus(
                    booking.getStatus(),
                    hold.getStatus(),
                    flight.getDepartureTime(),
                    seats,
                    tickets)),
            booking.getTotalAmount(),
            booking.getCurrency(),
            booking.getCreatedAt()),
        new SegmentView(
            flight.getId(),
            flight.getFlightNumber(),
            flight.getFlightDate(),
            flight.getDepartureTime(),
            flight.getArrivalTime(),
            durationMinutes(flight.getDepartureTime(), flight.getArrivalTime()),
            flight.getDepartureAirport().getCode(),
            flight.getDepartureAirport().getName(),
            flight.getDepartureAirport().getCity(),
            flight.getDepartureTerminal(),
            flight.getArrivalAirport().getCode(),
            flight.getArrivalAirport().getName(),
            flight.getArrivalAirport().getCity(),
            flight.getArrivalTerminal(),
            fareClass.getCode(),
            fareClass.getName(),
            fareClass.getCabinClass(),
            fareClass.getBaggageCheckinKg(),
            fareClass.getBaggageHandKg()),
        passengers.stream()
            .map(
                passenger ->
                    toPassengerView(
                        passenger,
                        seatByPassengerId.get(passenger.getId()),
                        ticketByPassengerId.get(passenger.getId())))
            .toList(),
        new PaymentView(
            payment == null
                ? fallbackPaymentStatus(booking.getStatus())
                : payment.getPaymentStatus(),
            paymentStatusLabel(
                payment == null
                    ? fallbackPaymentStatus(booking.getStatus())
                    : payment.getPaymentStatus()),
            payment == null ? null : payment.getPaymentMethod(),
            payment == null ? null : payment.getTransactionId(),
            payment == null ? booking.getTotalAmount() : nonNullMoney(payment.getAmount()),
            payment == null ? booking.getCurrency() : payment.getCurrency()),
        new ActionsView(
            canManageBooking(booking.getStatus()), !tickets.isEmpty(), !tickets.isEmpty()));
  }

  private PassengerView toPassengerView(
      PassengerEntity passenger, PassengerSeatAssignmentEntity seat, TicketEntity ticket) {
    return new PassengerView(
        passenger.getId(),
        passenger.getFirstName(),
        passenger.getLastName(),
        fullName(passenger),
        passenger.getPassengerType(),
        ticket == null
            ? null
            : new TicketView(
                ticket.getId(), ticket.getTicketNumber(), ticket.getStatus(), ticket.getIssuedAt()),
        seat == null
            ? null
            : new SeatView(
                seat.getFlightSeat().getId(),
                seat.getFlightSeat().getSeatNumber(),
                seat.getStatus(),
                nonNullMoney(seat.getSurchargeAmount()),
                seat.getCurrency()));
  }

  private String classifyTripStatus(
      String bookingStatus,
      String holdStatus,
      OffsetDateTime departureTime,
      List<PassengerSeatAssignmentEntity> seats,
      List<TicketEntity> tickets) {
    if ("CANCELLED".equalsIgnoreCase(bookingStatus)
        || "EXPIRED".equalsIgnoreCase(bookingStatus)
        || "RELEASED".equalsIgnoreCase(holdStatus)
        || (!tickets.isEmpty()
            && tickets.stream()
                .allMatch(ticket -> "CANCELLED".equalsIgnoreCase(ticket.getStatus())))) {
      return "CANCELLED";
    }
    if (departureTime != null && !departureTime.isBefore(OffsetDateTime.now())) {
      return "UPCOMING";
    }
    return "COMPLETED";
  }

  private boolean canManageBooking(String bookingStatus) {
    String normalized = normalize(bookingStatus);
    return "PENDING_PAYMENT".equals(normalized) || "CONFIRMED".equals(normalized);
  }

  private String bookingStatusLabel(String status) {
    return switch (normalize(status)) {
      case "CONFIRMED" -> "Confirmed";
      case "PENDING_PAYMENT" -> "Pending payment";
      case "EXPIRED" -> "Expired";
      case "CANCELLED" -> "Cancelled";
      default -> status;
    };
  }

  private String tripStatusLabel(String status) {
    return switch (normalize(status)) {
      case "UPCOMING" -> "Upcoming";
      case "COMPLETED" -> "Completed";
      case "CANCELLED" -> "Cancelled";
      default -> status;
    };
  }

  private String paymentStatusLabel(String status) {
    return switch (normalize(status)) {
      case "SUCCESS" -> "Paid";
      case "PENDING" -> "Pending payment";
      case "FAILED" -> "Payment failed";
      case "CANCELLED" -> "Payment cancelled";
      case "EXPIRED" -> "Payment expired";
      case "REFUNDED" -> "Refunded";
      case "PARTIALLY_REFUNDED" -> "Partially refunded";
      default -> status;
    };
  }

  private String fallbackPaymentStatus(String bookingStatus) {
    return switch (normalize(bookingStatus)) {
      case "CONFIRMED" -> "SUCCESS";
      case "PENDING_PAYMENT" -> "PENDING";
      case "EXPIRED" -> "EXPIRED";
      case "CANCELLED" -> "CANCELLED";
      default -> "UNKNOWN";
    };
  }

  private int durationMinutes(OffsetDateTime departure, OffsetDateTime arrival) {
    if (departure == null || arrival == null) {
      return 0;
    }
    return Math.max(0, (int) Duration.between(departure, arrival).toMinutes());
  }

  private String fullName(PassengerEntity passenger) {
    return (nullToBlank(passenger.getLastName()) + " " + nullToBlank(passenger.getFirstName()))
        .trim();
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String nullToBlank(String value) {
    return value == null ? "" : value;
  }

  private BigDecimal nonNullMoney(BigDecimal value) {
    return value == null
        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        : value.setScale(2, RoundingMode.HALF_UP);
  }

  public record TripDetailView(
      BookingSummaryView booking,
      SegmentView segment,
      List<PassengerView> passengers,
      PaymentView payment,
      ActionsView actions) {}

  public record BookingSummaryView(
      Long bookingId,
      String pnrCode,
      String bookingStatus,
      String bookingStatusLabel,
      String tripStatus,
      String tripStatusLabel,
      BigDecimal totalAmount,
      String currency,
      OffsetDateTime createdAt) {}

  public record SegmentView(
      Long flightId,
      String flightNumber,
      java.time.LocalDate flightDate,
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
      Integer carryOnBaggageKg) {}

  public record PassengerView(
      Long passengerId,
      String firstName,
      String lastName,
      String fullName,
      String passengerType,
      TicketView ticket,
      SeatView seat) {}

  public record TicketView(
      Long ticketId, String ticketNumber, String status, OffsetDateTime issuedAt) {}

  public record SeatView(
      Long seatId, String seatNumber, String status, BigDecimal surchargeAmount, String currency) {}

  public record PaymentView(
      String paymentStatus,
      String paymentStatusLabel,
      String paymentMethod,
      String transactionId,
      BigDecimal amount,
      String currency) {}

  public record ActionsView(
      boolean canManageBooking, boolean canViewETicket, boolean canDownloadInvoice) {}
}
