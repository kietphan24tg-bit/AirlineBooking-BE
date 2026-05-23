package com.example.modulepayment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulebooking.application.TicketIssuanceService;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.entity.PaymentWebhookEventEntity;
import com.example.modulepayment.gateway.PaymentRefundGateway.PaymentRefundResult;
import com.example.modulepayment.gateway.PayosWebhookSupport;
import com.example.modulepayment.gateway.PayosWebhookSupport.NormalizedPayosWebhookEvent;
import com.example.modulepayment.gateway.StripeWebhookSupport;
import com.example.modulepayment.gateway.StripeWebhookSupport.NormalizedStripeWebhookEvent;
import com.example.modulepayment.repository.PaymentRepository;
import com.example.modulepayment.repository.PaymentWebhookEventRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private PaymentWebhookEventRepository paymentWebhookEventRepository;
  @Mock private PayosWebhookSupport payosWebhookSupport;
  @Mock private StripeWebhookSupport stripeWebhookSupport;
  @Mock private FlightSeatRepository flightSeatRepository;
  @Mock private BookingFareHoldRepository bookingFareHoldRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;
  @Mock private TicketIssuanceService ticketIssuanceService;
  @Mock private BookingConfirmationNotifier bookingConfirmationNotifier;
  @Mock private TicketFinancialSnapshotService ticketFinancialSnapshotService;
  @Mock private PaymentRefundService paymentRefundService;

  private PaymentWebhookService paymentWebhookService;

  @BeforeEach
  void setUp() {
    paymentWebhookService =
        new PaymentWebhookService(
            paymentRepository,
            paymentWebhookEventRepository,
            payosWebhookSupport,
            stripeWebhookSupport,
            flightSeatRepository,
            bookingFareHoldRepository,
            passengerSeatAssignmentRepository,
            ticketIssuanceService,
            bookingConfirmationNotifier,
            ticketFinancialSnapshotService,
            paymentRefundService);
  }

  @Test
  void shouldMarkPaymentSuccessAndConfirmBooking() {
    NormalizedPayosWebhookEvent event =
        new NormalizedPayosWebhookEvent(
            "PAYOS",
            "PAYMENT_SUCCEEDED",
            123L,
            "plink_123",
            1200000L,
            "VND",
            "970415",
            "VietinBank",
            "NGUYEN VAN A",
            "0123456789",
            OffsetDateTime.parse("2025-08-10T03:03:09Z"),
            "signature",
            "{\"code\":\"00\"}");
    PaymentEntity payment = payment("PENDING", "PENDING_PAYMENT");

    when(payosWebhookSupport.parseAndVerify("payload")).thenReturn(event);
    when(paymentWebhookEventRepository.existsByProviderAndIdempotencyKey(
            "PAYOS", event.idempotencyKey()))
        .thenReturn(false);
    when(paymentRepository.findByTransactionIdForUpdate("plink_123"))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.findByIdForUpdate(123L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    paymentWebhookService.handlePayosWebhook("payload");

    assertEquals("SUCCESS", payment.getPaymentStatus());
    assertEquals("CONFIRMED", payment.getBooking().getStatus());
    verify(bookingFareHoldRepository).confirmHeldByBookingId(any(), any());
    verify(flightSeatRepository).bookReservedForBooking(10L);
    verify(passengerSeatAssignmentRepository).bookReservedByBookingId(any(), any());
    verify(ticketIssuanceService).issueTicketsForConfirmedBooking(10L);
    verify(ticketFinancialSnapshotService).ensureSnapshotsForBooking(payment.getBooking());
    verify(bookingConfirmationNotifier).sendIfNeeded(10L);
    ArgumentCaptor<PaymentWebhookEventEntity> captor =
        ArgumentCaptor.forClass(PaymentWebhookEventEntity.class);
    verify(paymentWebhookEventRepository).save(captor.capture());
    assertEquals("PROCESSED", captor.getValue().getProcessingStatus());
    assertEquals("PAYOS_123_plink_123_PAYMENT_SUCCEEDED", captor.getValue().getIdempotencyKey());
  }

  @Test
  void shouldAcceptSuspendedPayLaterWebhookAndConfirmBooking() {
    NormalizedPayosWebhookEvent event =
        new NormalizedPayosWebhookEvent(
            "PAYOS",
            "PAYMENT_SUCCEEDED",
            123L,
            "plink_123",
            1200000L,
            "VND",
            null,
            null,
            null,
            null,
            OffsetDateTime.parse("2025-08-10T03:03:09Z"),
            "signature",
            "{\"code\":\"00\"}");
    PaymentEntity payment = payment("SUSPENDED", "PENDING_PAYMENT");
    payment.setPaymentMethod("PAY_LATER");

    when(payosWebhookSupport.parseAndVerify("payload")).thenReturn(event);
    when(paymentWebhookEventRepository.existsByProviderAndIdempotencyKey(
            "PAYOS", event.idempotencyKey()))
        .thenReturn(false);
    when(paymentRepository.findByTransactionIdForUpdate("plink_123"))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.findByIdForUpdate(123L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    paymentWebhookService.handlePayosWebhook("payload");

    assertEquals("SUCCESS", payment.getPaymentStatus());
    assertEquals("CONFIRMED", payment.getBooking().getStatus());
    verify(ticketIssuanceService).issueTicketsForConfirmedBooking(10L);
  }

  @Test
  void shouldSkipDuplicateWebhook() {
    NormalizedPayosWebhookEvent event =
        new NormalizedPayosWebhookEvent(
            "PAYOS",
            "PAYMENT_SUCCEEDED",
            123L,
            "plink_123",
            1200000L,
            "VND",
            null,
            null,
            null,
            null,
            OffsetDateTime.parse("2025-08-10T03:03:09Z"),
            "signature",
            "{\"code\":\"00\"}");

    when(payosWebhookSupport.parseAndVerify("payload")).thenReturn(event);
    when(paymentWebhookEventRepository.existsByProviderAndIdempotencyKey(
            "PAYOS", event.idempotencyKey()))
        .thenReturn(true);

    paymentWebhookService.handlePayosWebhook("payload");

    verify(paymentRepository, never()).findByTransactionIdForUpdate(any());
    verify(paymentWebhookEventRepository, never()).save(any());
    verify(ticketFinancialSnapshotService, never())
        .ensureSnapshotsForBooking(any(BookingEntity.class));
  }

  @Test
  void shouldMarkStripeCheckoutPaymentSuccessAndConfirmBooking() {
    NormalizedStripeWebhookEvent event =
        new NormalizedStripeWebhookEvent(
            "STRIPE",
            "checkout.session.completed",
            123L,
            "cs_test_123",
            "pi_test_123",
            1200000L,
            "vnd",
            "paid",
            OffsetDateTime.parse("2025-08-10T03:03:09Z"),
            "evt_123",
            "t=1,v1=sig",
            "{\"id\":\"evt_123\"}");
    PaymentEntity payment = payment("PENDING", "PENDING_PAYMENT");
    payment.setPaymentMethod("STRIPE_CARD");
    payment.setTransactionId("cs_test_123");

    when(stripeWebhookSupport.parseAndVerify("payload", "signature")).thenReturn(event);
    when(paymentWebhookEventRepository.existsByProviderAndIdempotencyKey("STRIPE", "evt_123"))
        .thenReturn(false);
    when(paymentRepository.findByTransactionIdForUpdate("cs_test_123"))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.findByIdForUpdate(123L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    paymentWebhookService.handleStripeWebhook("payload", "signature");

    assertEquals("SUCCESS", payment.getPaymentStatus());
    assertEquals("CONFIRMED", payment.getBooking().getStatus());
    verify(bookingFareHoldRepository).confirmHeldByBookingId(any(), any());
    verify(flightSeatRepository).bookReservedForBooking(10L);
    verify(passengerSeatAssignmentRepository).bookReservedByBookingId(any(), any());
    verify(ticketIssuanceService).issueTicketsForConfirmedBooking(10L);
    verify(ticketFinancialSnapshotService).ensureSnapshotsForBooking(payment.getBooking());
    verify(bookingConfirmationNotifier).sendIfNeeded(10L);
    ArgumentCaptor<PaymentWebhookEventEntity> captor =
        ArgumentCaptor.forClass(PaymentWebhookEventEntity.class);
    verify(paymentWebhookEventRepository).save(captor.capture());
    assertEquals("STRIPE", captor.getValue().getProvider());
    assertEquals("evt_123", captor.getValue().getIdempotencyKey());
    assertEquals("PROCESSED", captor.getValue().getProcessingStatus());
  }

  @Test
  void shouldRefundLateStripeSuccessWhenBookingAlreadyExpired() {
    NormalizedStripeWebhookEvent event =
        new NormalizedStripeWebhookEvent(
            "STRIPE",
            "checkout.session.completed",
            123L,
            "cs_test_123",
            "pi_test_123",
            1200000L,
            "vnd",
            "paid",
            OffsetDateTime.parse("2025-08-10T03:03:09Z"),
            "evt_456",
            "t=1,v1=sig",
            "{\"id\":\"evt_456\"}");
    PaymentEntity payment = payment("PENDING", "PENDING_PAYMENT");
    payment.setPaymentMethod("STRIPE_CARD");
    payment.setTransactionId("cs_test_123");
    payment.getBooking().setPnrExpiresAt(OffsetDateTime.now().minusMinutes(1));

    when(stripeWebhookSupport.parseAndVerify("payload", "signature")).thenReturn(event);
    when(paymentWebhookEventRepository.existsByProviderAndIdempotencyKey("STRIPE", "evt_456"))
        .thenReturn(false);
    when(paymentRepository.findByTransactionIdForUpdate("cs_test_123"))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.findByIdForUpdate(123L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));
    when(paymentRefundService.refund(any(), any(), any(), any(), any()))
        .thenReturn(new PaymentRefundResult("re_123", "succeeded"));

    paymentWebhookService.handleStripeWebhook("payload", "signature");

    assertEquals("EXPIRED", payment.getPaymentStatus());
    assertEquals("EXPIRED", payment.getBooking().getStatus());
    verify(paymentRefundService).preparePaymentForRefund(payment);
    verify(paymentRefundService).refund(any(), any(), any(), any(), any());
    verify(ticketIssuanceService, never()).issueTicketsForConfirmedBooking(any());
    verify(bookingConfirmationNotifier, never()).sendIfNeeded(any());
  }

  @Test
  void shouldRejectLatePayosSuccessWhenBookingAlreadyExpired() {
    NormalizedPayosWebhookEvent event =
        new NormalizedPayosWebhookEvent(
            "PAYOS",
            "PAYMENT_SUCCEEDED",
            123L,
            "plink_123",
            1200000L,
            "VND",
            null,
            null,
            null,
            null,
            OffsetDateTime.parse("2025-08-10T03:03:09Z"),
            "signature",
            "{\"code\":\"00\"}");
    PaymentEntity payment = payment("PENDING", "EXPIRED");

    when(payosWebhookSupport.parseAndVerify("payload")).thenReturn(event);
    when(paymentWebhookEventRepository.existsByProviderAndIdempotencyKey(
            "PAYOS", event.idempotencyKey()))
        .thenReturn(false);
    when(paymentRepository.findByTransactionIdForUpdate("plink_123"))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.findByIdForUpdate(123L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    paymentWebhookService.handlePayosWebhook("payload");

    assertEquals("EXPIRED", payment.getPaymentStatus());
    assertEquals("EXPIRED", payment.getBooking().getStatus());
    verify(ticketIssuanceService, never()).issueTicketsForConfirmedBooking(any());
    verify(bookingConfirmationNotifier, never()).sendIfNeeded(any());
  }

  @Test
  void shouldRefundLatePayosSuccessWhenBookingAlreadySettledByAnotherPayment() {
    NormalizedPayosWebhookEvent event =
        new NormalizedPayosWebhookEvent(
            "PAYOS",
            "PAYMENT_SUCCEEDED",
            123L,
            "plink_123",
            1200000L,
            "VND",
            "970415",
            "VietinBank",
            "NGUYEN VAN A",
            "0123456789",
            OffsetDateTime.parse("2025-08-10T03:03:09Z"),
            "signature",
            "{\"code\":\"00\"}");
    PaymentEntity payment = payment("SUSPENDED", "CONFIRMED");
    payment.setPaymentMethod("PAY_LATER");

    when(payosWebhookSupport.parseAndVerify("payload")).thenReturn(event);
    when(paymentWebhookEventRepository.existsByProviderAndIdempotencyKey(
            "PAYOS", event.idempotencyKey()))
        .thenReturn(false);
    when(paymentRepository.findByTransactionIdForUpdate("plink_123"))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.findByIdForUpdate(123L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));
    when(paymentRefundService.refund(any(), any(), any(), any(), any()))
        .thenReturn(new PaymentRefundResult("refund_123", "SUCCEEDED"));

    paymentWebhookService.handlePayosWebhook("payload");

    assertEquals("FAILED", payment.getPaymentStatus());
    assertEquals("CONFIRMED", payment.getBooking().getStatus());
    verify(paymentRefundService).preparePaymentForRefund(payment);
    verify(paymentRefundService).refund(any(), any(), any(), any(), any());
    ArgumentCaptor<PaymentWebhookEventEntity> captor =
        ArgumentCaptor.forClass(PaymentWebhookEventEntity.class);
    verify(paymentWebhookEventRepository).save(captor.capture());
    assertEquals("PAYMENT_ALREADY_SETTLED", captor.getValue().getErrorCode());
    verify(ticketIssuanceService, never()).issueTicketsForConfirmedBooking(any());
  }

  @Test
  void shouldNotPersistAuditOnSystemError() {
    NormalizedPayosWebhookEvent event =
        new NormalizedPayosWebhookEvent(
            "PAYOS",
            "PAYMENT_SUCCEEDED",
            123L,
            "plink_123",
            1200000L,
            "VND",
            null,
            null,
            null,
            null,
            OffsetDateTime.parse("2025-08-10T03:03:09Z"),
            "signature",
            "{\"code\":\"00\"}");
    PaymentEntity payment = payment("PENDING", "PENDING_PAYMENT");

    when(payosWebhookSupport.parseAndVerify("payload")).thenReturn(event);
    when(paymentWebhookEventRepository.existsByProviderAndIdempotencyKey(
            "PAYOS", event.idempotencyKey()))
        .thenReturn(false);
    when(paymentRepository.findByTransactionIdForUpdate("plink_123"))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.findByIdForUpdate(123L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenThrow(new IllegalStateException("db-down"));

    assertThrows(
        IllegalStateException.class, () -> paymentWebhookService.handlePayosWebhook("payload"));

    verify(paymentWebhookEventRepository, never()).save(any());
  }

  @Test
  void shouldRejectSuccessWebhookWhenPaymentNotPending() {
    NormalizedPayosWebhookEvent event =
        new NormalizedPayosWebhookEvent(
            "PAYOS",
            "PAYMENT_SUCCEEDED",
            123L,
            "plink_123",
            1200000L,
            "VND",
            null,
            null,
            null,
            null,
            OffsetDateTime.parse("2025-08-10T03:03:09Z"),
            "signature",
            "{\"code\":\"00\"}");
    PaymentEntity payment = payment("FAILED", "PENDING_PAYMENT");

    when(payosWebhookSupport.parseAndVerify("payload")).thenReturn(event);
    when(paymentWebhookEventRepository.existsByProviderAndIdempotencyKey(
            "PAYOS", event.idempotencyKey()))
        .thenReturn(false);
    when(paymentRepository.findByTransactionIdForUpdate("plink_123"))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.findByIdForUpdate(123L)).thenReturn(Optional.of(payment));

    paymentWebhookService.handlePayosWebhook("payload");

    assertEquals("FAILED", payment.getPaymentStatus());
    assertEquals("PENDING_PAYMENT", payment.getBooking().getStatus());
    ArgumentCaptor<PaymentWebhookEventEntity> captor =
        ArgumentCaptor.forClass(PaymentWebhookEventEntity.class);
    verify(paymentWebhookEventRepository).save(captor.capture());
    assertEquals("REJECTED", captor.getValue().getProcessingStatus());
    assertEquals("PAYMENT_NOT_PENDING", captor.getValue().getErrorCode());
    verify(paymentRepository, never()).save(any());
  }

  @Test
  void shouldRejectFailureWebhookWhenPaymentNotPending() {
    NormalizedPayosWebhookEvent event =
        new NormalizedPayosWebhookEvent(
            "PAYOS",
            "PAYMENT_FAILED",
            123L,
            "plink_123",
            1200000L,
            "VND",
            null,
            null,
            null,
            null,
            OffsetDateTime.parse("2025-08-10T03:03:09Z"),
            "signature",
            "{\"code\":\"01\"}");
    PaymentEntity payment = payment("SUCCESS", "CONFIRMED");

    when(payosWebhookSupport.parseAndVerify("payload")).thenReturn(event);
    when(paymentWebhookEventRepository.existsByProviderAndIdempotencyKey(
            "PAYOS", event.idempotencyKey()))
        .thenReturn(false);
    when(paymentRepository.findByTransactionIdForUpdate("plink_123"))
        .thenReturn(Optional.of(payment));
    when(paymentRepository.findByIdForUpdate(123L)).thenReturn(Optional.of(payment));

    paymentWebhookService.handlePayosWebhook("payload");

    assertEquals("SUCCESS", payment.getPaymentStatus());
    assertEquals("CONFIRMED", payment.getBooking().getStatus());
    ArgumentCaptor<PaymentWebhookEventEntity> captor =
        ArgumentCaptor.forClass(PaymentWebhookEventEntity.class);
    verify(paymentWebhookEventRepository).save(captor.capture());
    assertEquals("REJECTED", captor.getValue().getProcessingStatus());
    assertEquals("PAYMENT_NOT_PENDING", captor.getValue().getErrorCode());
    verify(paymentRepository, never()).save(any());
  }

  @Test
  void shouldPropagateInvalidSignatureWithoutAudit() {
    AppException invalidSignature =
        new AppException("PAYMENT_WEBHOOK_INVALID_SIGNATURE", "Invalid signature", 401);
    when(payosWebhookSupport.parseAndVerify("payload")).thenThrow(invalidSignature);

    AppException exception =
        assertThrows(AppException.class, () -> paymentWebhookService.handlePayosWebhook("payload"));

    assertEquals("PAYMENT_WEBHOOK_INVALID_SIGNATURE", exception.getCode());
    verify(paymentWebhookEventRepository, never()).save(any());
  }

  private static PaymentEntity payment(String paymentStatus, String bookingStatus) {
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setStatus(bookingStatus);
    booking.setCurrency("VND");
    booking.setPnrExpiresAt(OffsetDateTime.now().plusMinutes(15));
    booking.setUpdatedAt(OffsetDateTime.now().minusMinutes(1));

    PaymentEntity payment = new PaymentEntity();
    payment.setId(123L);
    payment.setBooking(booking);
    payment.setAmount(new BigDecimal("1200000"));
    payment.setCurrency("VND");
    payment.setPaymentStatus(paymentStatus);
    payment.setTransactionId("plink_123");
    payment.setUpdatedAt(OffsetDateTime.now().minusMinutes(1));
    return payment;
  }
}
