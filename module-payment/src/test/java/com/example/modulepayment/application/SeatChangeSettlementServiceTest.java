package com.example.modulepayment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.entity.SeatCategoryEntity;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.SeatChangeSettlementEntity;
import com.example.modulepayment.entity.SeatChangeSettlementItemEntity;
import com.example.modulepayment.repository.BookingRepository;
import com.example.modulepayment.repository.PaymentRepository;
import com.example.modulepayment.repository.SeatChangeSettlementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeatChangeSettlementServiceTest {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

  @Mock private BookingRepository bookingRepository;
  @Mock private PassengerRepository passengerRepository;
  @Mock private BookingFareHoldRepository bookingFareHoldRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  @Mock private FlightSeatRepository flightSeatRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private SeatChangeSettlementRepository seatChangeSettlementRepository;
  @Mock private BookingChangePaymentGuard bookingChangePaymentGuard;
  @Mock private PaymentRefundService paymentRefundService;
  @Mock private PaymentProperties paymentProperties;

  private SeatChangeSettlementService service;

  @BeforeEach
  void setUp() {
    service =
        new SeatChangeSettlementService(
            bookingRepository,
            passengerRepository,
            bookingFareHoldRepository,
            passengerSeatAssignmentRepository,
            flightSeatRepository,
            paymentRepository,
            seatChangeSettlementRepository,
            bookingChangePaymentGuard,
            paymentRefundService,
            paymentProperties);

    Mockito.lenient()
        .when(seatChangeSettlementRepository.save(any(SeatChangeSettlementEntity.class)))
        .thenAnswer(
            invocation -> {
              SeatChangeSettlementEntity entity =
                  invocation.getArgument(0, SeatChangeSettlementEntity.class);
              if (entity.getId() == null) {
                entity.setId(500L);
              }
              return entity;
            });
  }

  @Test
  void confirmShouldApplySeatChangesAndSaveSettlementForNoneType() {
    Long bookingId = 10L;
    Long userId = 1L;
    Long passengerId = 100L;
    Long flightId = 200L;
    Long fromSeatId = 301L;
    Long toSeatId = 302L;

    BookingEntity booking = createBooking(bookingId, "CONFIRMED");
    PassengerEntity passenger = createPassenger(passengerId, booking);
    FlightEntity flight = createFlight(flightId);
    BookingFareHoldEntity hold = createHold(booking, flight);

    FlightSeatEntity fromSeat = createSeat(fromSeatId, flight, "10A");
    FlightSeatEntity toSeat = createSeat(toSeatId, flight, "10B");

    PassengerSeatAssignmentEntity currentAssignment =
        createAssignment(booking, passenger, fromSeat, "BOOKED");

    when(seatChangeSettlementRepository.findFirstByBookingIdAndIdempotencyKeyWithItems(
            eq(bookingId), eq("idem-none")))
        .thenReturn(Optional.empty());
    when(bookingRepository.findByIdAndUserIdForUpdate(bookingId, userId))
        .thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(bookingId)).thenReturn(List.of(passenger));
    when(bookingFareHoldRepository.findByBookingIdOrderByIdAsc(bookingId))
        .thenReturn(List.of(hold));
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(eq(bookingId), anyList()))
        .thenReturn(List.of(currentAssignment));
    when(flightSeatRepository.findAllById(any())).thenReturn(List.of(fromSeat, toSeat));
    when(flightSeatRepository.releaseSelectedForBooking(bookingId, List.of(fromSeatId)))
        .thenReturn(1);
    when(flightSeatRepository.bookSelectedForBooking(bookingId, List.of(toSeatId))).thenReturn(1);
    when(passengerRepository.findByIdAndBookingId(passengerId, bookingId))
        .thenReturn(Optional.of(passenger));

    SeatChangeSettlementService.ChangeCommand command =
        new SeatChangeSettlementService.ChangeCommand(passengerId, flightId, fromSeatId, toSeatId);

    SeatChangeConfirmView confirmView =
        service.confirm(userId, bookingId, List.of(command), "idem-none", ZERO, ZERO, ZERO);

    assertNotNull(confirmView);
    assertEquals(500L, confirmView.settlementId());
    assertEquals("COMPLETED", confirmView.status());
    assertEquals("NONE", confirmView.settlementType());

    verify(passengerSeatAssignmentRepository).save(currentAssignment);
    assertEquals("RELEASED", currentAssignment.getStatus());
    verify(flightSeatRepository).releaseSelectedForBooking(bookingId, List.of(fromSeatId));
    verify(flightSeatRepository).bookSelectedForBooking(bookingId, List.of(toSeatId));
    verify(passengerSeatAssignmentRepository, Mockito.times(2))
        .save(any(PassengerSeatAssignmentEntity.class));
    verify(seatChangeSettlementRepository).save(any(SeatChangeSettlementEntity.class));
  }

  @Test
  void confirmShouldThrowStaleWhenFromSeatMismatchesCurrentAssignment() {
    Long bookingId = 10L;
    Long userId = 1L;
    Long passengerId = 100L;
    Long flightId = 200L;
    Long fromSeatId = 301L;
    Long wrongFromSeatId = 399L;
    Long toSeatId = 302L;

    BookingEntity booking = createBooking(bookingId, "CONFIRMED");
    PassengerEntity passenger = createPassenger(passengerId, booking);
    FlightEntity flight = createFlight(flightId);
    BookingFareHoldEntity hold = createHold(booking, flight);
    FlightSeatEntity actualSeat = createSeat(fromSeatId, flight, "10A");
    FlightSeatEntity wrongFromSeat = createSeat(wrongFromSeatId, flight, "99A");
    FlightSeatEntity targetSeat = createSeat(toSeatId, flight, "10B");

    PassengerSeatAssignmentEntity currentAssignment =
        createAssignment(booking, passenger, actualSeat, "BOOKED");

    when(seatChangeSettlementRepository.findFirstByBookingIdAndIdempotencyKeyWithItems(
            eq(bookingId), eq("idem-stale")))
        .thenReturn(Optional.empty());
    when(bookingRepository.findByIdAndUserIdForUpdate(bookingId, userId))
        .thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(bookingId)).thenReturn(List.of(passenger));
    when(bookingFareHoldRepository.findByBookingIdOrderByIdAsc(bookingId))
        .thenReturn(List.of(hold));
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(eq(bookingId), anyList()))
        .thenReturn(List.of(currentAssignment));
    when(flightSeatRepository.findAllById(any())).thenReturn(List.of(wrongFromSeat, targetSeat));

    SeatChangeSettlementService.ChangeCommand command =
        new SeatChangeSettlementService.ChangeCommand(
            passengerId, flightId, wrongFromSeatId, toSeatId);

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.confirm(
                    userId, bookingId, List.of(command), "idem-stale", ZERO, ZERO, ZERO));

    assertEquals("SEAT_CHANGE_STALE", exception.getCode());
  }

  @Test
  void confirmShouldRollbackAppliedSeatsWhenRefundFails() {
    Long bookingId = 10L;
    Long userId = 1L;
    Long passengerId = 100L;
    Long flightId = 200L;
    Long fromSeatId = 301L;
    Long toSeatId = 302L;

    BookingEntity booking = createBooking(bookingId, "CONFIRMED");
    PassengerEntity passenger = createPassenger(passengerId, booking);
    FlightEntity flight = createFlight(flightId);
    BookingFareHoldEntity hold = createHold(booking, flight);
    hold.getFlightFare().getFareClass().setSeatSelectionPolicy("PAID");

    FlightSeatEntity fromSeat = createSeat(fromSeatId, flight, "10A");
    FlightSeatEntity toSeat = createSeat(toSeatId, flight, "10B");
    fromSeat.getSeatCategory().setSurchargeAmount(new BigDecimal("100000"));
    toSeat.getSeatCategory().setSurchargeAmount(ZERO);

    PassengerSeatAssignmentEntity currentAssignment =
        createAssignment(booking, passenger, fromSeat, "BOOKED");
    com.example.modulepayment.entity.PaymentEntity payment =
        new com.example.modulepayment.entity.PaymentEntity();
    payment.setId(88L);
    payment.setAmount(new BigDecimal("500000"));
    payment.setPaymentStatus("SUCCESS");

    when(seatChangeSettlementRepository.findFirstByBookingIdAndIdempotencyKeyWithItems(
            eq(bookingId), eq("idem-refund-fail")))
        .thenReturn(Optional.empty());
    when(bookingRepository.findByIdAndUserIdForUpdate(bookingId, userId))
        .thenReturn(Optional.of(booking));
    when(passengerRepository.findByBookingIdOrderById(bookingId)).thenReturn(List.of(passenger));
    when(bookingFareHoldRepository.findByBookingIdOrderByIdAsc(bookingId))
        .thenReturn(List.of(hold));
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(eq(bookingId), anyList()))
        .thenReturn(List.of(currentAssignment));
    when(flightSeatRepository.findAllById(any())).thenReturn(List.of(fromSeat, toSeat));
    when(flightSeatRepository.releaseSelectedForBooking(bookingId, List.of(fromSeatId)))
        .thenReturn(1);
    when(flightSeatRepository.bookSelectedForBooking(bookingId, List.of(toSeatId))).thenReturn(1);
    when(passengerRepository.findByIdAndBookingId(passengerId, bookingId))
        .thenReturn(Optional.of(passenger));
    when(paymentRepository
            .findBookingLevelPaymentsByBookingIdAndPaymentStatusInForUpdateOrderByLatest(
                eq(bookingId), anyList()))
        .thenReturn(List.of(payment));
    when(paymentRefundService.refund(anyLong(), any(), any(), any(), any()))
        .thenThrow(new AppException("GATEWAY_TIMEOUT", "Gateway timed out", 504));
    when(flightSeatRepository.releaseSelectedForBooking(bookingId, List.of(toSeatId)))
        .thenReturn(1);
    when(flightSeatRepository.findById(fromSeatId)).thenReturn(Optional.of(fromSeat));
    when(flightSeatRepository.bookSelectedForBooking(bookingId, List.of(fromSeatId))).thenReturn(1);

    SeatChangeSettlementService.ChangeCommand command =
        new SeatChangeSettlementService.ChangeCommand(passengerId, flightId, fromSeatId, toSeatId);

    SeatChangeConfirmView confirmView =
        service.confirm(
            userId,
            bookingId,
            List.of(command),
            "idem-refund-fail",
            ZERO,
            new BigDecimal("100000"),
            new BigDecimal("-100000"));

    assertNotNull(confirmView);
    assertEquals("REFUND_FAILED", confirmView.status());
    assertEquals("FAILED", confirmView.refundStatus());
    assertEquals("GATEWAY_TIMEOUT", confirmView.errorCode());

    verify(flightSeatRepository).releaseSelectedForBooking(bookingId, List.of(toSeatId));
    verify(flightSeatRepository).bookSelectedForBooking(bookingId, List.of(fromSeatId));
  }

  @Test
  void handlePaymentSuccessShouldApplySeatChangesExactlyOnce() {
    com.example.modulepayment.entity.PaymentEntity payment =
        new com.example.modulepayment.entity.PaymentEntity();
    BookingEntity booking = createBooking(10L, "PENDING_PAYMENT");
    PassengerEntity passenger = createPassenger(100L, booking);
    FlightEntity flight = createFlight(200L);
    FlightSeatEntity fromSeat = createSeat(301L, flight, "10A");
    FlightSeatEntity toSeat = createSeat(302L, flight, "10B");
    PassengerSeatAssignmentEntity currentAssignment =
        createAssignment(booking, passenger, fromSeat, "RESERVED");
    SeatChangeSettlementEntity settlement = new SeatChangeSettlementEntity();
    settlement.setId(77L);
    settlement.setBooking(booking);
    settlement.setStatus("PAYMENT_PENDING");
    SeatChangeSettlementItemEntity item = new SeatChangeSettlementItemEntity();
    item.setSettlement(settlement);
    item.setPassengerId(passenger.getId());
    item.setFlightId(flight.getId());
    item.setFromSeatId(fromSeat.getId());
    item.setFromSeatNumber(fromSeat.getSeatNumber());
    item.setToSeatId(toSeat.getId());
    item.setToSeatNumber(toSeat.getSeatNumber());
    item.setOldAmount(ZERO);
    item.setNewAmount(new BigDecimal("100000"));
    item.setDeltaAmount(new BigDecimal("100000"));
    settlement.getItems().add(item);
    payment.setSeatChangeSettlement(settlement);

    when(seatChangeSettlementRepository.findByIdWithItemsForUpdate(77L))
        .thenReturn(Optional.of(settlement));
    when(flightSeatRepository.findAllById(List.of(toSeat.getId()))).thenReturn(List.of(toSeat));
    when(passengerSeatAssignmentRepository.findByBookingIdAndStatusIn(eq(10L), anyList()))
        .thenReturn(List.of(currentAssignment));
    when(flightSeatRepository.releaseSelectedForBooking(10L, List.of(fromSeat.getId())))
        .thenReturn(1);
    when(flightSeatRepository.reserveSelectedForBooking(
            eq(10L), eq(List.of(toSeat.getId())), any()))
        .thenReturn(1);
    when(passengerRepository.findByIdAndBookingId(100L, 10L)).thenReturn(Optional.of(passenger));

    service.handlePaymentSuccess(payment, "ref-123");
    service.handlePaymentSuccess(payment, "ref-123");

    assertEquals("COMPLETED", settlement.getStatus());
    assertEquals("ref-123", payment.getProviderPaymentReference());
    verify(flightSeatRepository).releaseSelectedForBooking(10L, List.of(fromSeat.getId()));
    verify(flightSeatRepository)
        .reserveSelectedForBooking(eq(10L), eq(List.of(toSeat.getId())), any());
    verify(passengerSeatAssignmentRepository, Mockito.times(2))
        .save(any(PassengerSeatAssignmentEntity.class));
  }

  private BookingEntity createBooking(Long id, String status) {
    BookingEntity booking = new BookingEntity();
    booking.setId(id);
    booking.setStatus(status);
    booking.setCurrency("VND");
    booking.setPnrCode("XYZ123");
    return booking;
  }

  private PassengerEntity createPassenger(Long id, BookingEntity booking) {
    PassengerEntity passenger = new PassengerEntity();
    passenger.setId(id);
    passenger.setBooking(booking);
    passenger.setFirstName("John");
    passenger.setLastName("Doe");
    passenger.setPassengerType("ADULT");
    return passenger;
  }

  private FlightEntity createFlight(Long id) {
    FlightEntity flight = new FlightEntity();
    flight.setId(id);
    flight.setFlightNumber("VN123");
    AirportEntity dep = new AirportEntity();
    dep.setCode("SGN");
    AirportEntity arr = new AirportEntity();
    arr.setCode("HAN");
    flight.setDepartureAirport(dep);
    flight.setArrivalAirport(arr);
    flight.setDepartureTime(OffsetDateTime.now().plusDays(1));
    return flight;
  }

  private BookingFareHoldEntity createHold(BookingEntity booking, FlightEntity flight) {
    BookingFareHoldEntity hold = new BookingFareHoldEntity();
    hold.setBooking(booking);
    hold.setStatus("CONFIRMED");
    FlightFareEntity fare = new FlightFareEntity();
    fare.setFlight(flight);
    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setSeatSelectionPolicy("INCLUDED");
    SeatCategoryEntity includedCat = new SeatCategoryEntity();
    includedCat.setPricingRank(10);
    fareClass.setIncludedSeatCategory(includedCat);
    fare.setFareClass(fareClass);
    hold.setFlightFare(fare);
    return hold;
  }

  private FlightSeatEntity createSeat(Long id, FlightEntity flight, String seatNumber) {
    FlightSeatEntity seat = new FlightSeatEntity();
    seat.setId(id);
    seat.setFlight(flight);
    seat.setSeatNumber(seatNumber);
    SeatCategoryEntity cat = new SeatCategoryEntity();
    cat.setPricingRank(5);
    cat.setSurchargeAmount(ZERO);
    seat.setSeatCategory(cat);
    return seat;
  }

  private PassengerSeatAssignmentEntity createAssignment(
      BookingEntity booking, PassengerEntity passenger, FlightSeatEntity seat, String status) {
    PassengerSeatAssignmentEntity assignment = new PassengerSeatAssignmentEntity();
    assignment.setBooking(booking);
    assignment.setPassenger(passenger);
    assignment.setFlightSeat(seat);
    assignment.setStatus(status);
    return assignment;
  }
}
