package com.example.appbootstrap.application.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.controller.booking.dto.SeatChangeRequest;
import com.example.appbootstrap.controller.booking.dto.SeatAssignmentRequest;
import com.example.appbootstrap.controller.booking.dto.SeatAssignmentResponse;
import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.entity.SeatCategoryEntity;
import com.example.moduleflight.entity.SeatClassEntity;
import com.example.moduleflight.entity.SeatTypeEntity;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingSeatAssignmentServiceTest {

  @Mock private BookingRepository bookingRepository;
  @Mock private BookingFareHoldRepository bookingFareHoldRepository;
  @Mock private PassengerRepository passengerRepository;
  @Mock private FlightSeatRepository flightSeatRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private BookingServiceRepository bookingServiceRepository;

  private BookingSeatAssignmentService service;

  @BeforeEach
  void setUp() {
    service =
        new BookingSeatAssignmentService(
            bookingRepository,
            bookingFareHoldRepository,
            passengerRepository,
            flightSeatRepository,
            passengerSeatAssignmentRepository,
            paymentRepository,
            new BookingTotalRecalculationService(
                bookingRepository, bookingServiceRepository, passengerSeatAssignmentRepository));
  }

  @Test
  void assignSeats_paidSeatRefreshesBookingTotal() {
    BookingEntity booking = booking("PENDING_PAYMENT", "1400.00");
    BookingFareHoldEntity hold = hold("PAID", null, 1);
    PassengerEntity passenger = passenger(1L, booking, "ADULT");
    FlightSeatEntity seat =
        seat(
            100L,
            hold.getFlightFare().getFlight(),
            category("STANDARD", 1, "0.00"),
            type("WINDOW", "20000.00"));

    stubSingleSeatSelection(booking, hold, passenger, seat);
    when(bookingServiceRepository.sumPriceByBookingId(10L))
        .thenReturn(BigDecimal.ZERO, BigDecimal.ZERO);
    when(passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(10L))
        .thenReturn(BigDecimal.ZERO, new BigDecimal("20000.00"), new BigDecimal("20000.00"));
    when(passengerSeatAssignmentRepository.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SeatAssignmentResponse response =
        service.assignSeats(
            7L,
            10L,
            new SeatAssignmentRequest(
                List.of(new SeatAssignmentRequest.AssignmentRequest(1L, 100L))));

    assertEquals(new BigDecimal("21400.00"), booking.getTotalAmount());
    assertEquals(new BigDecimal("20000.00"), response.seatSurchargeTotal());
    assertEquals(new BigDecimal("21400.00"), response.totalAmount());
    assertEquals(new BigDecimal("20000.00"), response.assignments().getFirst().surchargeAmount());
    verify(bookingRepository).save(booking);
  }

  @Test
  void assignSeats_freeSeatKeepsBookingTotal() {
    BookingEntity booking = booking("PENDING_PAYMENT", "1400.00");
    BookingFareHoldEntity hold = hold("PAID", null, 1);
    PassengerEntity passenger = passenger(1L, booking, "ADULT");
    FlightSeatEntity seat =
        seat(
            100L,
            hold.getFlightFare().getFlight(),
            category("STANDARD", 1, "0.00"),
            type("MIDDLE", "0.00"));

    stubSingleSeatSelection(booking, hold, passenger, seat);
    when(bookingServiceRepository.sumPriceByBookingId(10L))
        .thenReturn(BigDecimal.ZERO, BigDecimal.ZERO);
    when(passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(10L))
        .thenReturn(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    when(passengerSeatAssignmentRepository.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SeatAssignmentResponse response =
        service.assignSeats(
            7L,
            10L,
            new SeatAssignmentRequest(
                List.of(new SeatAssignmentRequest.AssignmentRequest(1L, 100L))));

    assertEquals(new BigDecimal("1400.00"), booking.getTotalAmount());
    assertEquals(new BigDecimal("0.00"), response.seatSurchargeTotal());
    assertEquals(new BigDecimal("0.00"), response.assignments().getFirst().surchargeAmount());
  }

  @Test
  void assignSeats_multiplePaidSeatsRefreshTotal() {
    BookingEntity booking = booking("PENDING_PAYMENT", "1400.00");
    BookingFareHoldEntity hold = hold("PAID", null, 2);
    PassengerEntity firstPassenger = passenger(1L, booking, "ADULT");
    PassengerEntity secondPassenger = passenger(2L, booking, "ADULT");
    FlightEntity flight = hold.getFlightFare().getFlight();
    FlightSeatEntity firstSeat =
        seat(100L, flight, category("PREFERRED", 2, "40000.00"), type("WINDOW", "20000.00"));
    FlightSeatEntity secondSeat =
        seat(101L, flight, category("EXTRA_LEGROOM", 4, "180000.00"), type("AISLE", "20000.00"));

    stubBaseSelection(
        booking, hold, List.of(firstPassenger, secondPassenger), List.of(firstSeat, secondSeat));
    when(bookingServiceRepository.sumPriceByBookingId(10L))
        .thenReturn(BigDecimal.ZERO, BigDecimal.ZERO);
    when(passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(10L))
        .thenReturn(BigDecimal.ZERO, new BigDecimal("260000.00"), new BigDecimal("260000.00"));
    when(passengerSeatAssignmentRepository.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SeatAssignmentResponse response =
        service.assignSeats(
            7L,
            10L,
            new SeatAssignmentRequest(
                List.of(
                    new SeatAssignmentRequest.AssignmentRequest(1L, 100L),
                    new SeatAssignmentRequest.AssignmentRequest(2L, 101L))));

    assertEquals(new BigDecimal("261400.00"), booking.getTotalAmount());
    assertEquals(new BigDecimal("260000.00"), response.seatSurchargeTotal());
  }

  @Test
  void assignSeats_confirmedBookingRejectsPaidSeat() {
    BookingEntity booking = booking("CONFIRMED", "1400.00");
    BookingFareHoldEntity hold = hold("PAID", null, 1);
    FlightSeatEntity seat =
        seat(
            100L,
            hold.getFlightFare().getFlight(),
            category("STANDARD", 1, "0.00"),
            type("WINDOW", "20000.00"));

    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(bookingFareHoldRepository.findFirstByBookingIdAndStatusIn(any(), any()))
        .thenReturn(Optional.of(hold));
    when(passengerSeatAssignmentRepository.countByBookingIdAndStatusIn(any(), any()))
        .thenReturn(0L);
    when(flightSeatRepository.findAllowedSeatIdsForFare(any(), any())).thenReturn(Set.of(100L));
    when(flightSeatRepository.findAllById(List.of(100L))).thenReturn(List.of(seat));
    when(passengerSeatAssignmentRepository.existsByFlightSeatIdAndStatusIn(
            100L, List.of("RESERVED", "BOOKED")))
        .thenReturn(false);

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.assignSeats(
                    7L,
                    10L,
                    new SeatAssignmentRequest(
                        List.of(new SeatAssignmentRequest.AssignmentRequest(1L, 100L)))));

    assertEquals("SEAT_PAYMENT_REQUIRED", exception.getCode());
    verify(passengerSeatAssignmentRepository, never()).saveAll(any());
  }

  @Test
  void unassignSeats_releasesSeatsAndClearsSurchargeWhenNoneRemain() {
    BookingEntity booking = booking("CONFIRMED", "5000.00");
    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);

    PassengerEntity passenger = passenger(1L, booking, "ADULT");
    FlightEntity flight = new FlightEntity();
    flight.setId(1L);
    FlightSeatEntity flightSeat = new FlightSeatEntity();
    flightSeat.setId(100L);
    flightSeat.setFlight(flight);

    PassengerSeatAssignmentEntity psa = new PassengerSeatAssignmentEntity();
    psa.setPassenger(passenger);
    psa.setFlightSeat(flightSeat);
    psa.setStatus("BOOKED");
    psa.setSurchargeAmount(new BigDecimal("0.00"));
    psa.setCurrency("VND");

    when(passengerSeatAssignmentRepository.findFirstByActiveAssignment(
            10L, 1L, 100L, List.of("RESERVED", "BOOKED")))
        .thenReturn(Optional.of(psa));
    when(passengerSeatAssignmentRepository.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(flightSeatRepository.releaseSelectedForBooking(10L, List.of(100L))).thenReturn(1);
    when(passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(10L))
        .thenReturn(BigDecimal.ZERO);
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(eq(10L), any()))
        .thenReturn(List.of());

    SeatAssignmentResponse response =
        service.unassignSeats(
            7L,
            10L,
            new SeatAssignmentRequest(
                List.of(new SeatAssignmentRequest.AssignmentRequest(1L, 100L))));

    assertEquals("NONE", response.status());
    assertTrue(response.assignments().isEmpty());
    assertEquals(0, response.seatSurchargeTotal().signum());
    assertEquals("RELEASED", psa.getStatus());
    verify(flightSeatRepository).releaseSelectedForBooking(10L, List.of(100L));
  }

  @Test
  void unassignSeats_throwsWhenNoActiveAssignment() {
    BookingEntity booking = booking("CONFIRMED", "5000.00");
    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(passengerSeatAssignmentRepository.findFirstByActiveAssignment(
            10L, 1L, 100L, List.of("RESERVED", "BOOKED")))
        .thenReturn(Optional.empty());

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.unassignSeats(
                    7L,
                    10L,
                    new SeatAssignmentRequest(
                        List.of(new SeatAssignmentRequest.AssignmentRequest(1L, 100L)))));

    assertEquals("SEAT_ASSIGNMENT_NOT_FOUND", exception.getCode());
    verify(flightSeatRepository, never()).releaseSelectedForBooking(any(), any());
  }

  @Test
  void unassignSeats_rejectsPendingPayment() {
    BookingEntity booking = booking("PENDING_PAYMENT", "1400.00");
    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(true);

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.unassignSeats(
                    7L,
                    10L,
                    new SeatAssignmentRequest(
                        List.of(new SeatAssignmentRequest.AssignmentRequest(1L, 100L)))));

    assertEquals("BOOKING_PAYMENT_ALREADY_STARTED", exception.getCode());
    verify(passengerSeatAssignmentRepository, never())
        .findFirstByActiveAssignment(any(), any(), any(), any());
  }

  @Test
  void assignSeats_rejectsPendingPayment() {
    BookingEntity booking = booking("PENDING_PAYMENT", "1400.00");
    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(true);

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.assignSeats(
                    7L,
                    10L,
                    new SeatAssignmentRequest(
                        List.of(new SeatAssignmentRequest.AssignmentRequest(1L, 100L)))));

    assertEquals("BOOKING_PAYMENT_ALREADY_STARTED", exception.getCode());
    verify(bookingFareHoldRepository, never()).findFirstByBookingIdAndStatusIn(any(), any());
  }

  @Test
  void changeSeat_cancelsAssignmentWhenTargetSeatMissing() {
    BookingEntity booking = booking("CONFIRMED", "5000.00");
    PassengerEntity passenger = passenger(1L, booking, "ADULT");
    PassengerSeatAssignmentEntity currentAssignment = activeAssignment(booking, passenger, 100L, 1L, "BOOKED");

    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(passengerSeatAssignmentRepository.findFirstByActiveAssignment(
            10L, 1L, 100L, List.of("RESERVED", "BOOKED")))
        .thenReturn(Optional.of(currentAssignment));
    when(passengerSeatAssignmentRepository.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(flightSeatRepository.releaseSelectedForBooking(10L, List.of(100L))).thenReturn(1);
    when(passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(10L))
        .thenReturn(BigDecimal.ZERO);
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(eq(10L), any()))
        .thenReturn(List.of());

    SeatAssignmentResponse response =
        service.changeSeat(7L, 10L, new SeatChangeRequest(1L, 100L, null));

    assertEquals("NONE", response.status());
    assertTrue(response.assignments().isEmpty());
    assertEquals("RELEASED", currentAssignment.getStatus());
  }

  @Test
  void changeSeat_switchesSeatAtomicallyForPendingPayment() {
    BookingEntity booking = booking("PENDING_PAYMENT", "1400.00");
    BookingFareHoldEntity hold = hold("PAID", null, 1);
    PassengerEntity passenger = passenger(1L, booking, "ADULT");
    PassengerSeatAssignmentEntity currentAssignment =
        activeAssignment(booking, passenger, 100L, hold.getFlightFare().getFlight().getId(), "RESERVED");
    FlightSeatEntity nextSeat =
        seat(
            101L,
            hold.getFlightFare().getFlight(),
            category("PREFERRED", 2, "40000.00"),
            type("AISLE", "20000.00"));

    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(passengerSeatAssignmentRepository.findFirstByActiveAssignment(
            10L, 1L, 100L, List.of("RESERVED", "BOOKED")))
        .thenReturn(Optional.of(currentAssignment));
    when(passengerSeatAssignmentRepository.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(flightSeatRepository.releaseSelectedForBooking(10L, List.of(100L))).thenReturn(1);
    when(bookingFareHoldRepository.findFirstByBookingIdAndStatusIn(any(), any()))
        .thenReturn(Optional.of(hold));
    when(flightSeatRepository.findById(101L)).thenReturn(Optional.of(nextSeat));
    when(flightSeatRepository.findAllowedSeatIdsForFare(any(), any())).thenReturn(Set.of(101L));
    when(passengerSeatAssignmentRepository.existsByFlightSeatIdAndStatusIn(
            101L, List.of("RESERVED", "BOOKED")))
        .thenReturn(false);
    when(flightSeatRepository.reserveSelectedForBooking(10L, List.of(101L), booking.getPnrExpiresAt()))
        .thenReturn(1);
    when(bookingServiceRepository.sumPriceByBookingId(10L))
        .thenReturn(BigDecimal.ZERO, BigDecimal.ZERO);
    when(passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(10L))
        .thenReturn(BigDecimal.ZERO, new BigDecimal("60000.00"), new BigDecimal("60000.00"));
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(eq(10L), any()))
        .thenReturn(
            List.of(
                activeAssignment(booking, passenger, 101L, hold.getFlightFare().getFlight().getId(), "RESERVED")));
    when(passengerSeatAssignmentRepository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SeatAssignmentResponse response =
        service.changeSeat(7L, 10L, new SeatChangeRequest(1L, 100L, 101L));

    assertEquals("RESERVED", response.status());
    assertEquals(new BigDecimal("61400.00"), response.totalAmount());
    assertEquals(new BigDecimal("60000.00"), response.seatSurchargeTotal());
    verify(flightSeatRepository).releaseSelectedForBooking(10L, List.of(100L));
    verify(flightSeatRepository).reserveSelectedForBooking(10L, List.of(101L), booking.getPnrExpiresAt());
    verify(passengerSeatAssignmentRepository)
        .save(
            argThat(
                entity ->
                    entity.getPassenger().getId().equals(1L)
                        && entity.getFlightSeat().getId().equals(101L)
                        && entity.getSurchargeAmount().compareTo(new BigDecimal("60000.00")) == 0));
  }

  @Test
  void changeSeat_rejectsPaidSeatForConfirmedBooking() {
    BookingEntity booking = booking("CONFIRMED", "5000.00");
    BookingFareHoldEntity hold = hold("PAID", null, 1);
    PassengerEntity passenger = passenger(1L, booking, "ADULT");
    PassengerSeatAssignmentEntity currentAssignment =
        activeAssignment(booking, passenger, 100L, hold.getFlightFare().getFlight().getId(), "BOOKED");
    FlightSeatEntity nextSeat =
        seat(
            101L,
            hold.getFlightFare().getFlight(),
            category("STANDARD", 1, "0.00"),
            type("WINDOW", "20000.00"));

    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(passengerSeatAssignmentRepository.findFirstByActiveAssignment(
            10L, 1L, 100L, List.of("RESERVED", "BOOKED")))
        .thenReturn(Optional.of(currentAssignment));
    when(passengerSeatAssignmentRepository.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(flightSeatRepository.releaseSelectedForBooking(10L, List.of(100L))).thenReturn(1);
    when(bookingFareHoldRepository.findFirstByBookingIdAndStatusIn(any(), any()))
        .thenReturn(Optional.of(hold));
    when(flightSeatRepository.findById(101L)).thenReturn(Optional.of(nextSeat));
    when(flightSeatRepository.findAllowedSeatIdsForFare(any(), any())).thenReturn(Set.of(101L));
    when(passengerSeatAssignmentRepository.existsByFlightSeatIdAndStatusIn(
            101L, List.of("RESERVED", "BOOKED")))
        .thenReturn(false);

    AppException exception =
        assertThrows(
            AppException.class, () -> service.changeSeat(7L, 10L, new SeatChangeRequest(1L, 100L, 101L)));

    assertEquals("SEAT_PAYMENT_REQUIRED", exception.getCode());
    verify(passengerSeatAssignmentRepository, never()).save(any());
  }

  @Test
  void changeSeat_rejectsNoopRequest() {
    BookingEntity booking = booking("CONFIRMED", "5000.00");
    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);

    AppException exception =
        assertThrows(
            AppException.class, () -> service.changeSeat(7L, 10L, new SeatChangeRequest(1L, 100L, 100L)));

    assertEquals("SEAT_CHANGE_NOOP", exception.getCode());
    verify(passengerSeatAssignmentRepository, never())
        .findFirstByActiveAssignment(any(), any(), any(), any());
  }

  @Test
  void changeSeat_rejectsWhenTargetSeatAlreadyAssigned() {
    BookingEntity booking = booking("CONFIRMED", "5000.00");
    BookingFareHoldEntity hold = hold("PAID", null, 1);
    PassengerEntity passenger = passenger(1L, booking, "ADULT");
    PassengerSeatAssignmentEntity currentAssignment =
        activeAssignment(booking, passenger, 100L, hold.getFlightFare().getFlight().getId(), "BOOKED");
    FlightSeatEntity nextSeat =
        seat(
            101L,
            hold.getFlightFare().getFlight(),
            category("STANDARD", 1, "0.00"),
            type("MIDDLE", "0.00"));

    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(passengerSeatAssignmentRepository.findFirstByActiveAssignment(
            10L, 1L, 100L, List.of("RESERVED", "BOOKED")))
        .thenReturn(Optional.of(currentAssignment));
    when(passengerSeatAssignmentRepository.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(flightSeatRepository.releaseSelectedForBooking(10L, List.of(100L))).thenReturn(1);
    when(bookingFareHoldRepository.findFirstByBookingIdAndStatusIn(any(), any()))
        .thenReturn(Optional.of(hold));
    when(flightSeatRepository.findById(101L)).thenReturn(Optional.of(nextSeat));
    when(flightSeatRepository.findAllowedSeatIdsForFare(any(), any())).thenReturn(Set.of(101L));
    when(passengerSeatAssignmentRepository.existsByFlightSeatIdAndStatusIn(
            101L, List.of("RESERVED", "BOOKED")))
        .thenReturn(true);

    AppException exception =
        assertThrows(
            AppException.class, () -> service.changeSeat(7L, 10L, new SeatChangeRequest(1L, 100L, 101L)));

    assertEquals("SEAT_UNAVAILABLE", exception.getCode());
    verify(passengerSeatAssignmentRepository, never()).save(any());
  }

  @Test
  void changeSeat_rejectsWhenSourceSeatCannotBeReleased() {
    BookingEntity booking = booking("CONFIRMED", "5000.00");
    PassengerEntity passenger = passenger(1L, booking, "ADULT");
    PassengerSeatAssignmentEntity currentAssignment = activeAssignment(booking, passenger, 100L, 1L, "BOOKED");

    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(passengerSeatAssignmentRepository.findFirstByActiveAssignment(
            10L, 1L, 100L, List.of("RESERVED", "BOOKED")))
        .thenReturn(Optional.of(currentAssignment));
    when(passengerSeatAssignmentRepository.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(flightSeatRepository.releaseSelectedForBooking(10L, List.of(100L))).thenReturn(0);

    AppException exception =
        assertThrows(
            AppException.class, () -> service.changeSeat(7L, 10L, new SeatChangeRequest(1L, 100L, null)));

    assertEquals("SEAT_RELEASE_FAILED", exception.getCode());
  }

  private void stubSingleSeatSelection(
      BookingEntity booking,
      BookingFareHoldEntity hold,
      PassengerEntity passenger,
      FlightSeatEntity seat) {
    stubBaseSelection(booking, hold, List.of(passenger), List.of(seat));
  }

  private void stubBaseSelection(
      BookingEntity booking,
      BookingFareHoldEntity hold,
      List<PassengerEntity> passengers,
      List<FlightSeatEntity> seats) {
    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.existsByBookingIdAndPaymentStatus(10L, "PENDING")).thenReturn(false);
    when(bookingFareHoldRepository.findFirstByBookingIdAndStatusIn(any(), any()))
        .thenReturn(Optional.of(hold));
    when(passengerSeatAssignmentRepository.countByBookingIdAndStatusIn(any(), any()))
        .thenReturn(0L);
    when(flightSeatRepository.findAllowedSeatIdsForFare(any(), any()))
        .thenReturn(
            seats.stream()
                .map(FlightSeatEntity::getId)
                .collect(java.util.stream.Collectors.toSet()));
    when(flightSeatRepository.findAllById(seats.stream().map(FlightSeatEntity::getId).toList()))
        .thenReturn(seats);
    for (FlightSeatEntity seat : seats) {
      when(passengerSeatAssignmentRepository.existsByFlightSeatIdAndStatusIn(
              seat.getId(), List.of("RESERVED", "BOOKED")))
          .thenReturn(false);
    }
    for (PassengerEntity passenger : passengers) {
      when(passengerRepository.findByIdAndBookingId(passenger.getId(), 10L))
          .thenReturn(Optional.of(passenger));
      when(passengerSeatAssignmentRepository.existsByPassengerIdAndStatusIn(
              passenger.getId(), List.of("RESERVED", "BOOKED")))
          .thenReturn(false);
    }
    when(flightSeatRepository.reserveSelectedForBooking(
            10L, seats.stream().map(FlightSeatEntity::getId).toList(), booking.getPnrExpiresAt()))
        .thenReturn(seats.size());
  }

  private static BookingEntity booking(String status, String totalAmount) {
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setStatus(status);
    booking.setPnrExpiresAt(OffsetDateTime.now().plusMinutes(10));
    booking.setTotalAmount(new BigDecimal(totalAmount));
    booking.setCurrency("VND");
    return booking;
  }

  private static BookingFareHoldEntity hold(
      String seatSelectionPolicy, SeatCategoryEntity includedSeatCategory, int quantity) {
    FlightEntity flight = new FlightEntity();
    flight.setId(4557L);
    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setId(5L);
    fareClass.setSeatSelectionPolicy(seatSelectionPolicy);
    fareClass.setIncludedSeatCategory(includedSeatCategory);
    FlightFareEntity fare = new FlightFareEntity();
    fare.setFlight(flight);
    fare.setFareClass(fareClass);
    BookingFareHoldEntity hold = new BookingFareHoldEntity();
    hold.setFlightFare(fare);
    hold.setQuantity(quantity);
    hold.setStatus("HELD");
    return hold;
  }

  private static PassengerEntity passenger(Long id, BookingEntity booking, String type) {
    PassengerEntity passenger = new PassengerEntity();
    passenger.setId(id);
    passenger.setBooking(booking);
    passenger.setPassengerType(type);
    return passenger;
  }

  private static FlightSeatEntity seat(
      Long id, FlightEntity flight, SeatCategoryEntity category, SeatTypeEntity type) {
    FlightSeatEntity seat = new FlightSeatEntity();
    seat.setId(id);
    seat.setFlight(flight);
    seat.setSeatClass(new SeatClassEntity());
    seat.setSeatCategory(category);
    seat.setSeatType(type);
    seat.setStatus("AVAILABLE");
    return seat;
  }

  private static PassengerSeatAssignmentEntity activeAssignment(
      BookingEntity booking, PassengerEntity passenger, Long seatId, Long flightId, String status) {
    FlightEntity flight = new FlightEntity();
    flight.setId(flightId);
    FlightSeatEntity seat = new FlightSeatEntity();
    seat.setId(seatId);
    seat.setFlight(flight);

    PassengerSeatAssignmentEntity assignment = new PassengerSeatAssignmentEntity();
    assignment.setBooking(booking);
    assignment.setPassenger(passenger);
    assignment.setFlightSeat(seat);
    assignment.setStatus(status);
    assignment.setSurchargeAmount(BigDecimal.ZERO.setScale(2));
    assignment.setCurrency("VND");
    return assignment;
  }

  private static SeatCategoryEntity category(String code, int rank, String surcharge) {
    SeatCategoryEntity category = new SeatCategoryEntity();
    category.setCode(code);
    category.setPricingRank(rank);
    category.setSurchargeAmount(new BigDecimal(surcharge));
    return category;
  }

  private static SeatTypeEntity type(String code, String surcharge) {
    SeatTypeEntity type = new SeatTypeEntity();
    type.setCode(code);
    type.setSurchargeAmount(new BigDecimal(surcharge));
    return type;
  }
}
