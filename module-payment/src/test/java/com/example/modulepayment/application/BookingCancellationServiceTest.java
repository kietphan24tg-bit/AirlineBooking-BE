package com.example.modulepayment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulebooking.application.TicketIssuanceService;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.FlightSeatEntity;
import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import com.example.modulebooking.entity.TicketEntity;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulebooking.repository.TicketRepository;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleidentity.entity.UserEntity;
import com.example.modulepayment.entity.BookingCancellationEntity;
import com.example.modulepayment.entity.BookingCancellationItemEntity;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.entity.TicketFinancialSnapshotEntity;
import com.example.modulepayment.gateway.PaymentRefundGateway.PaymentRefundResult;
import com.example.modulepayment.repository.BookingCancellationRepository;
import com.example.modulepayment.repository.BookingRepository;
import com.example.modulepayment.repository.PaymentRepository;
import com.example.modulepayment.repository.TicketFinancialSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingCancellationServiceTest {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

  @Mock private BookingRepository bookingRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private TicketRepository ticketRepository;
  @Mock private TicketFinancialSnapshotRepository ticketFinancialSnapshotRepository;
  @Mock private BookingCancellationRepository bookingCancellationRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  @Mock private FlightSeatRepository flightSeatRepository;
  @Mock private BookingServiceRepository bookingServiceRepository;
  @Mock private TicketIssuanceService ticketIssuanceService;
  @Mock private TicketFinancialSnapshotService ticketFinancialSnapshotService;
  @Mock private PaymentRefundService paymentRefundService;

  private BookingCancellationService service;

  @BeforeEach
  void setUp() {
    service =
        new BookingCancellationService(
            bookingRepository,
            paymentRepository,
            ticketRepository,
            ticketFinancialSnapshotRepository,
            bookingCancellationRepository,
            passengerSeatAssignmentRepository,
            flightSeatRepository,
            bookingServiceRepository,
            ticketIssuanceService,
            ticketFinancialSnapshotService,
            paymentRefundService);

    when(bookingRepository.save(any(BookingEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, BookingEntity.class));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));
    when(bookingCancellationRepository.save(any(BookingCancellationEntity.class)))
        .thenAnswer(
            invocation -> {
              BookingCancellationEntity cancellation =
                  invocation.getArgument(0, BookingCancellationEntity.class);
              if (cancellation.getId() == null) {
                cancellation.setId(900L);
              }
              AtomicLong nextItemId = new AtomicLong(1L);
              cancellation
                  .getItems()
                  .forEach(
                      item -> {
                        if (item.getId() == null) {
                          item.setId(nextItemId.getAndIncrement());
                        }
                      });
              return cancellation;
            });
  }

  @Test
  void previewShouldReturnPassengerLevelRefundBreakdown() {
    BookingEntity booking = booking("CONFIRMED");
    PaymentEntity payment = payment(booking, "SUCCESS");
    TicketEntity adultTicket =
        ticket(
            booking,
            7001L,
            1001L,
            "An",
            "Nguyen",
            "ADULT",
            "ISSUED",
            501L,
            true,
            new BigDecimal("150000"));
    TicketEntity childTicket =
        ticket(
            booking,
            7002L,
            1002L,
            "Binh",
            "Tran",
            "CHILD",
            "ISSUED",
            502L,
            true,
            new BigDecimal("150000"));
    TicketFinancialSnapshotEntity adultSnapshot =
        snapshot(
            booking,
            adultTicket,
            new BigDecimal("1000000"),
            new BigDecimal("100000"),
            new BigDecimal("900000"),
            new BigDecimal("50000"),
            new BigDecimal("20000"),
            new BigDecimal("150000"),
            true);
    TicketFinancialSnapshotEntity childSnapshot =
        snapshot(
            booking,
            childTicket,
            new BigDecimal("750000"),
            new BigDecimal("75000"),
            new BigDecimal("675000"),
            ZERO,
            new BigDecimal("10000"),
            new BigDecimal("150000"),
            true);

    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findByBookingIdAndPaymentStatusInOrderByLatest(10L, anyList()))
        .thenReturn(List.of(payment));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(adultTicket, childTicket));
    when(ticketFinancialSnapshotRepository.findByBookingIdWithDetails(10L))
        .thenReturn(List.of(adultSnapshot, childSnapshot));

    BookingCancellationPreviewView result = service.preview(7L, 10L, List.of(1001L));

    assertEquals(10L, result.bookingId());
    assertEquals("CONFIRMED", result.bookingStatus());
    assertEquals("SUCCESS", result.paymentStatus());
    assertEquals(1, result.items().size());
    assertEquals("An Nguyen", result.items().getFirst().passengerName());
    assertEquals(new BigDecimal("820000.00"), result.summary().netRefund());
    assertEquals(new BigDecimal("150000.00"), result.summary().refundFee());
    verify(ticketIssuanceService).issueTicketsForBooking(7L, 10L);
    verify(ticketFinancialSnapshotService).ensureSnapshotsForBooking(booking);
  }

  @Test
  void cancelShouldPartiallyRefundAndKeepBookingConfirmed() {
    BookingEntity booking = booking("CONFIRMED");
    PaymentEntity payment = payment(booking, "SUCCESS");
    TicketEntity cancelledTicket =
        ticket(
            booking,
            7001L,
            1001L,
            "An",
            "Nguyen",
            "ADULT",
            "ISSUED",
            701L,
            true,
            new BigDecimal("150000"));
    TicketEntity remainingTicket =
        ticket(
            booking,
            7002L,
            1002L,
            "Binh",
            "Tran",
            "ADULT",
            "ISSUED",
            702L,
            true,
            new BigDecimal("150000"));
    TicketFinancialSnapshotEntity cancelledSnapshot =
        snapshot(
            booking,
            cancelledTicket,
            new BigDecimal("1000000"),
            new BigDecimal("100000"),
            new BigDecimal("900000"),
            new BigDecimal("50000"),
            new BigDecimal("20000"),
            new BigDecimal("150000"),
            true);
    TicketFinancialSnapshotEntity remainingSnapshot =
        snapshot(
            booking,
            remainingTicket,
            new BigDecimal("1000000"),
            ZERO,
            new BigDecimal("1000000"),
            ZERO,
            ZERO,
            new BigDecimal("150000"),
            true);
    PassengerSeatAssignmentEntity assignment =
        assignment(booking, cancelledTicket.getPassenger(), 701L, "BOOKED", new BigDecimal("50000"));

    when(bookingCancellationRepository.findByBookingIdAndIdempotencyKeyWithItems(10L, "idem-1"))
        .thenReturn(Optional.empty());
    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findByBookingIdAndPaymentStatusInForUpdateOrderByLatest(10L, anyList()))
        .thenReturn(List.of(payment));
    when(ticketRepository.findByBookingIdAndPassengerIdInWithDetailsForUpdate(10L, List.of(1001L)))
        .thenReturn(List.of(cancelledTicket));
    when(ticketFinancialSnapshotRepository.findByBookingIdWithDetails(10L))
        .thenReturn(List.of(cancelledSnapshot, remainingSnapshot));
    when(paymentRefundService.providerOf(payment)).thenReturn("PAYOS");
    when(paymentRefundService.refund(
            anyLong(),
            any(),
            any(PaymentEntity.class),
            any(BigDecimal.class),
            any()))
        .thenReturn(new PaymentRefundResult("refund_123", "SUCCEEDED"));
    when(passengerSeatAssignmentRepository.findByBookingIdAndPassengerIdInAndStatusIn(
            anyLong(), anyCollection(), anyCollection()))
        .thenReturn(List.of(assignment));
    when(flightSeatRepository.releaseSelectedForBooking(10L, List.of(701L))).thenReturn(1);
    when(ticketRepository.countByBookingIdAndStatusNot(10L, "CANCELLED")).thenReturn(1L);

    BookingCancellationResultView result =
        service.cancel(
            7L,
            10L,
            List.of(1001L),
            "Need to cancel",
            "idem-1",
            new BigDecimal("820000"),
            new BigDecimal("150000"));

    assertEquals("SUCCEEDED", result.refundStatus());
    assertEquals("PAYOS", result.provider());
    assertEquals("refund_123", result.providerRefundId());
    assertEquals("CONFIRMED", result.bookingStatus());
    assertEquals("PARTIALLY_REFUNDED", result.paymentStatus());
    assertEquals("CANCELLED", cancelledTicket.getStatus());
    assertEquals("RELEASED", assignment.getStatus());
    assertEquals("PARTIALLY_REFUNDED", payment.getPaymentStatus());
    verify(bookingServiceRepository).deleteByBookingIdAndPassengerIdIn(10L, List.of(1001L));
  }

  @Test
  void cancelShouldFullyRefundAndCancelBookingWhenNoTicketRemains() {
    BookingEntity booking = booking("CONFIRMED");
    PaymentEntity payment = payment(booking, "SUCCESS");
    TicketEntity ticket =
        ticket(
            booking,
            7001L,
            1001L,
            "An",
            "Nguyen",
            "ADULT",
            "ISSUED",
            null,
            true,
            BigDecimal.ZERO);
    TicketFinancialSnapshotEntity snapshot =
        snapshot(
            booking,
            ticket,
            new BigDecimal("1000000"),
            ZERO,
            new BigDecimal("1000000"),
            ZERO,
            ZERO,
            BigDecimal.ZERO,
            true);

    when(bookingCancellationRepository.findByBookingIdAndIdempotencyKeyWithItems(
            10L, "idem-full"))
        .thenReturn(Optional.empty());
    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findByBookingIdAndPaymentStatusInForUpdateOrderByLatest(10L, anyList()))
        .thenReturn(List.of(payment));
    when(ticketRepository.findByBookingIdAndPassengerIdInWithDetailsForUpdate(10L, List.of(1001L)))
        .thenReturn(List.of(ticket));
    when(ticketFinancialSnapshotRepository.findByBookingIdWithDetails(10L))
        .thenReturn(List.of(snapshot));
    when(paymentRefundService.providerOf(payment)).thenReturn("PAYOS");
    when(paymentRefundService.refund(
            anyLong(),
            any(),
            any(PaymentEntity.class),
            any(BigDecimal.class),
            any()))
        .thenReturn(new PaymentRefundResult("refund_full", "SUCCEEDED"));
    when(passengerSeatAssignmentRepository.findByBookingIdAndPassengerIdInAndStatusIn(
            anyLong(), anyCollection(), anyCollection()))
        .thenReturn(List.of());
    when(ticketRepository.countByBookingIdAndStatusNot(10L, "CANCELLED")).thenReturn(0L);

    BookingCancellationResultView result =
        service.cancel(
            7L,
            10L,
            List.of(1001L),
            "Cancel all",
            "idem-full",
            new BigDecimal("1000000"),
            BigDecimal.ZERO);

    assertEquals("SUCCEEDED", result.refundStatus());
    assertEquals("CANCELLED", result.bookingStatus());
    assertEquals("REFUNDED", result.paymentStatus());
    assertEquals("CANCELLED", ticket.getStatus());
    assertEquals("CANCELLED", booking.getStatus());
    assertEquals("REFUNDED", payment.getPaymentStatus());
  }

  @Test
  void previewShouldRejectNonRefundableTicket() {
    BookingEntity booking = booking("CONFIRMED");
    PaymentEntity payment = payment(booking, "SUCCESS");
    TicketEntity ticket =
        ticket(
            booking,
            7001L,
            1001L,
            "An",
            "Nguyen",
            "ADULT",
            "ISSUED",
            null,
            false,
            new BigDecimal("700000"));
    TicketFinancialSnapshotEntity snapshot =
        snapshot(
            booking,
            ticket,
            new BigDecimal("1000000"),
            ZERO,
            new BigDecimal("1000000"),
            ZERO,
            ZERO,
            new BigDecimal("700000"),
            false);

    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findByBookingIdAndPaymentStatusInOrderByLatest(10L, anyList()))
        .thenReturn(List.of(payment));
    when(ticketRepository.findByBookingIdWithDetails(10L)).thenReturn(List.of(ticket));
    when(ticketFinancialSnapshotRepository.findByBookingIdWithDetails(10L))
        .thenReturn(List.of(snapshot));

    AppException exception =
        assertThrows(AppException.class, () -> service.preview(7L, 10L, List.of(1001L)));

    assertEquals("TICKET_NOT_REFUNDABLE", exception.getCode());
  }

  @Test
  void cancelShouldRejectWhenPreviewTotalsAreStale() {
    BookingEntity booking = booking("CONFIRMED");
    PaymentEntity payment = payment(booking, "SUCCESS");
    TicketEntity ticket =
        ticket(
            booking,
            7001L,
            1001L,
            "An",
            "Nguyen",
            "ADULT",
            "ISSUED",
            null,
            true,
            new BigDecimal("150000"));
    TicketFinancialSnapshotEntity snapshot =
        snapshot(
            booking,
            ticket,
            new BigDecimal("1000000"),
            new BigDecimal("100000"),
            new BigDecimal("900000"),
            ZERO,
            ZERO,
            new BigDecimal("150000"),
            true);

    when(bookingCancellationRepository.findByBookingIdAndIdempotencyKeyWithItems(
            10L, "idem-stale"))
        .thenReturn(Optional.empty());
    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findByBookingIdAndPaymentStatusInForUpdateOrderByLatest(10L, anyList()))
        .thenReturn(List.of(payment));
    when(ticketRepository.findByBookingIdAndPassengerIdInWithDetailsForUpdate(10L, List.of(1001L)))
        .thenReturn(List.of(ticket));
    when(ticketFinancialSnapshotRepository.findByBookingIdWithDetails(10L))
        .thenReturn(List.of(snapshot));

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                service.cancel(
                    7L,
                    10L,
                    List.of(1001L),
                    "Need to cancel",
                    "idem-stale",
                    new BigDecimal("1"),
                    new BigDecimal("150000")));

    assertEquals("CANCELLATION_PREVIEW_STALE", exception.getCode());
    verify(bookingCancellationRepository, never()).save(any(BookingCancellationEntity.class));
    verify(paymentRefundService, never()).refund(anyLong(), any(), any(PaymentEntity.class), any(), any());
  }

  @Test
  void cancelShouldReturnExistingResultForSameIdempotencyKey() {
    BookingEntity booking = booking("CONFIRMED");
    PaymentEntity payment = payment(booking, "PARTIALLY_REFUNDED");
    TicketEntity ticket =
        ticket(
            booking,
            7001L,
            1001L,
            "An",
            "Nguyen",
            "ADULT",
            "CANCELLED",
            null,
            true,
            new BigDecimal("150000"));

    BookingCancellationEntity existing = new BookingCancellationEntity();
    existing.setId(901L);
    existing.setBooking(booking);
    existing.setPayment(payment);
    existing.setRequestedByUser(booking.getUser());
    existing.setIdempotencyKey("idem-existing");
    existing.setRequestHash(
        requestHash(
            List.of(1001L), "Need to cancel", new BigDecimal("820000"), new BigDecimal("150000")));
    existing.setProvider("PAYOS");
    existing.setRefundStatus("SUCCEEDED");
    existing.setProviderRefundId("refund_existing");
    existing.setCurrency("VND");
    existing.setFareGrossTotal(new BigDecimal("1000000"));
    existing.setPromoAllocatedTotal(new BigDecimal("100000"));
    existing.setFareNetTotal(new BigDecimal("900000"));
    existing.setSeatRefundTotal(new BigDecimal("50000"));
    existing.setServiceRefundTotal(new BigDecimal("20000"));
    existing.setRefundFeeTotal(new BigDecimal("150000"));
    existing.setNetRefundTotal(new BigDecimal("820000"));

    BookingCancellationItemEntity item = new BookingCancellationItemEntity();
    item.setId(1L);
    item.setCancellation(existing);
    item.setTicket(ticket);
    item.setPassenger(ticket.getPassenger());
    item.setFareGrossAmount(new BigDecimal("1000000"));
    item.setPromoAllocatedAmount(new BigDecimal("100000"));
    item.setFareNetAmount(new BigDecimal("900000"));
    item.setSeatRefundAmount(new BigDecimal("50000"));
    item.setServiceRefundAmount(new BigDecimal("20000"));
    item.setRefundFeeAmount(new BigDecimal("150000"));
    item.setNetRefundAmount(new BigDecimal("820000"));
    existing.getItems().add(item);

    when(bookingCancellationRepository.findByBookingIdAndIdempotencyKeyWithItems(
            10L, "idem-existing"))
        .thenReturn(Optional.of(existing));

    BookingCancellationResultView result =
        service.cancel(
            7L,
            10L,
            List.of(1001L),
            "Need to cancel",
            "idem-existing",
            new BigDecimal("820000"),
            new BigDecimal("150000"));

    assertEquals(901L, result.cancellationId());
    assertEquals("SUCCEEDED", result.refundStatus());
    assertEquals("refund_existing", result.providerRefundId());
    verifyNoInteractions(ticketIssuanceService, ticketFinancialSnapshotService, paymentRefundService);
  }

  @Test
  void cancelShouldReturnFailedResultWhenRefundProviderThrows() {
    BookingEntity booking = booking("CONFIRMED");
    PaymentEntity payment = payment(booking, "SUCCESS");
    TicketEntity ticket =
        ticket(
            booking,
            7001L,
            1001L,
            "An",
            "Nguyen",
            "ADULT",
            "ISSUED",
            null,
            true,
            new BigDecimal("150000"));
    TicketFinancialSnapshotEntity snapshot =
        snapshot(
            booking,
            ticket,
            new BigDecimal("1000000"),
            new BigDecimal("100000"),
            new BigDecimal("900000"),
            ZERO,
            ZERO,
            new BigDecimal("150000"),
            true);

    when(bookingCancellationRepository.findByBookingIdAndIdempotencyKeyWithItems(
            10L, "idem-failed"))
        .thenReturn(Optional.empty());
    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findByBookingIdAndPaymentStatusInForUpdateOrderByLatest(10L, anyList()))
        .thenReturn(List.of(payment));
    when(ticketRepository.findByBookingIdAndPassengerIdInWithDetailsForUpdate(10L, List.of(1001L)))
        .thenReturn(List.of(ticket));
    when(ticketFinancialSnapshotRepository.findByBookingIdWithDetails(10L))
        .thenReturn(List.of(snapshot));
    when(paymentRefundService.providerOf(payment)).thenReturn("PAYOS");
    when(paymentRefundService.refund(
            anyLong(),
            any(),
            any(PaymentEntity.class),
            any(BigDecimal.class),
            any()))
        .thenThrow(new AppException("PAYMENT_PROVIDER_ERROR", "Provider down", 502));

    BookingCancellationResultView result =
        service.cancel(
            7L,
            10L,
            List.of(1001L),
            "Need to cancel",
            "idem-failed",
            new BigDecimal("750000"),
            new BigDecimal("150000"));

    assertEquals("FAILED", result.refundStatus());
    assertEquals("CONFIRMED", result.bookingStatus());
    assertEquals("SUCCESS", result.paymentStatus());
    assertEquals("ISSUED", ticket.getStatus());
    assertEquals("SUCCESS", payment.getPaymentStatus());
    verify(bookingCancellationRepository).save(any(BookingCancellationEntity.class));
  }

  private static BookingEntity booking(String status) {
    UserEntity user = new UserEntity();
    user.setId(7L);
    user.setEmail("user@example.com");
    user.setFirstName("Demo");
    user.setLastName("User");

    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setUser(user);
    booking.setPnrCode("ABC123");
    booking.setContactEmail("user@example.com");
    booking.setContactPhone("0901234567");
    booking.setTotalAmount(new BigDecimal("2000000"));
    booking.setCurrency("VND");
    booking.setDiscountAmount(new BigDecimal("100000"));
    booking.setStatus(status);
    booking.setPnrExpiresAt(OffsetDateTime.now().plusHours(1));
    booking.setCreatedAt(OffsetDateTime.now().minusDays(1));
    booking.setUpdatedAt(OffsetDateTime.now().minusMinutes(10));
    return booking;
  }

  private static PaymentEntity payment(BookingEntity booking, String status) {
    PaymentEntity payment = new PaymentEntity();
    payment.setId(123L);
    payment.setBooking(booking);
    payment.setAmount(new BigDecimal("2000000"));
    payment.setCurrency("VND");
    payment.setPaymentMethod("PAYOS_QR");
    payment.setPaymentStatus(status);
    payment.setTransactionId("plink_123");
    payment.setCreatedAt(OffsetDateTime.now().minusHours(2));
    payment.setUpdatedAt(OffsetDateTime.now().minusMinutes(5));
    return payment;
  }

  private static TicketEntity ticket(
      BookingEntity booking,
      Long ticketId,
      Long passengerId,
      String firstName,
      String lastName,
      String passengerType,
      String ticketStatus,
      Long seatId,
      boolean refundable,
      BigDecimal refundFee) {
    PassengerEntity passenger = new PassengerEntity();
    passenger.setId(passengerId);
    passenger.setBooking(booking);
    passenger.setFirstName(firstName);
    passenger.setLastName(lastName);
    passenger.setDateOfBirth(LocalDate.parse("1990-01-01"));
    passenger.setGender("MALE");
    passenger.setPassengerType(passengerType);
    passenger.setCreatedAt(OffsetDateTime.now().minusDays(1));
    passenger.setUpdatedAt(OffsetDateTime.now().minusDays(1));

    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setId(201L + ticketId);
    fareClass.setCode("BUSINESS");
    fareClass.setName("Business");
    fareClass.setRefundable(refundable);
    fareClass.setRefundFee(normalizeMoney(refundFee));

    FlightFareEntity flightFare = new FlightFareEntity();
    flightFare.setId(301L + ticketId);
    flightFare.setTotalPrice(new BigDecimal("1000000"));

    TicketEntity ticket = new TicketEntity();
    ticket.setId(ticketId);
    ticket.setBooking(booking);
    ticket.setPassenger(passenger);
    ticket.setFareClass(fareClass);
    ticket.setFlightFare(flightFare);
    ticket.setStatus(ticketStatus);
    ticket.setTicketNumber("738-" + ticketId);
    ticket.setIssuedAt(OffsetDateTime.now().minusHours(3));
    ticket.setCreatedAt(OffsetDateTime.now().minusDays(1));
    ticket.setUpdatedAt(OffsetDateTime.now().minusMinutes(30));
    if (seatId != null) {
      FlightSeatEntity seat = new FlightSeatEntity();
      seat.setId(seatId);
      ticket.setFlightSeat(seat);
    }
    return ticket;
  }

  private static TicketFinancialSnapshotEntity snapshot(
      BookingEntity booking,
      TicketEntity ticket,
      BigDecimal fareGross,
      BigDecimal promoAllocated,
      BigDecimal fareNet,
      BigDecimal seatRefund,
      BigDecimal serviceRefund,
      BigDecimal refundFee,
      boolean refundable) {
    TicketFinancialSnapshotEntity snapshot = new TicketFinancialSnapshotEntity();
    snapshot.setId(ticket.getId() + 1000);
    snapshot.setBooking(booking);
    snapshot.setTicket(ticket);
    snapshot.setPassenger(ticket.getPassenger());
    snapshot.setFareGrossAmount(normalizeMoney(fareGross));
    snapshot.setPromoAllocatedAmount(normalizeMoney(promoAllocated));
    snapshot.setFareNetAmount(normalizeMoney(fareNet));
    snapshot.setSeatRefundAmount(normalizeMoney(seatRefund));
    snapshot.setServiceRefundAmount(normalizeMoney(serviceRefund));
    snapshot.setRefundFeeAmount(normalizeMoney(refundFee));
    snapshot.setRefundable(refundable);
    snapshot.setPassengerType(ticket.getPassenger().getPassengerType());
    snapshot.setCurrency("VND");
    snapshot.setCreatedAt(OffsetDateTime.now().minusHours(1));
    snapshot.setUpdatedAt(OffsetDateTime.now().minusHours(1));
    return snapshot;
  }

  private static PassengerSeatAssignmentEntity assignment(
      BookingEntity booking,
      PassengerEntity passenger,
      Long seatId,
      String status,
      BigDecimal surchargeAmount) {
    FlightSeatEntity seat = new FlightSeatEntity();
    seat.setId(seatId);

    PassengerSeatAssignmentEntity assignment = new PassengerSeatAssignmentEntity();
    assignment.setId(8001L);
    assignment.setBooking(booking);
    assignment.setPassenger(passenger);
    assignment.setFlightSeat(seat);
    assignment.setStatus(status);
    assignment.setSurchargeAmount(normalizeMoney(surchargeAmount));
    assignment.setCurrency("VND");
    assignment.setCreatedAt(OffsetDateTime.now().minusDays(1));
    assignment.setUpdatedAt(OffsetDateTime.now().minusHours(2));
    return assignment;
  }

  private static String requestHash(
      List<Long> passengerIds,
      String reason,
      BigDecimal expectedNetRefund,
      BigDecimal expectedRefundFee) {
    String payload =
        passengerIds.stream().map(String::valueOf).collect(Collectors.joining(","))
            + "|"
            + (reason == null ? "" : reason)
            + "|"
            + normalizeMoney(expectedNetRefund)
            + "|"
            + normalizeMoney(expectedRefundFee);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static BigDecimal normalizeMoney(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }
}
