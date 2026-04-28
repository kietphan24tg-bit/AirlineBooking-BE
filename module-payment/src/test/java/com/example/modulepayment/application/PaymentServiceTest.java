package com.example.modulepayment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.exception.PaymentNotFoundException;
import com.example.modulepayment.gateway.PaymentGateway;
import com.example.modulepayment.gateway.PaymentGateway.PaymentGatewayResult;
import com.example.modulepayment.repository.BookingRepository;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock private BookingRepository bookingRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private PaymentGateway paymentGateway;

  private PaymentService paymentService;

  @BeforeEach
  void setUp() {
    paymentService =
        new PaymentService(
            bookingRepository,
            paymentRepository,
            paymentGateway,
            new PaymentProperties(
                "payos",
                "webhook-secret",
                "http://localhost:3000/payment/success",
                "http://localhost:3000/payment/cancel",
                "http://localhost:8080",
                new PaymentProperties.Payos(
                    "client-id", "api-key", "checksum-key", "https://api-merchant.payos.vn")));
  }

  @Test
  void shouldRejectMissingBookingForOwner() {
    when(bookingRepository.findByIdAndUserIdForUpdate(99L, 7L)).thenReturn(Optional.empty());

    AppException exception =
        assertThrows(
            AppException.class,
            () -> paymentService.createPayment(new CreatePaymentCommand(99L, 7L, "PAYOS")));

    assertEquals("BOOKING_NOT_FOUND", exception.getCode());
  }

  @Test
  void shouldRejectExpiredBooking() {
    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L))
        .thenReturn(Optional.of(booking("PENDING", OffsetDateTime.now().minusMinutes(1))));

    AppException exception =
        assertThrows(
            AppException.class,
            () -> paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS")));

    assertEquals("BOOKING_EXPIRED", exception.getCode());
  }

  @Test
  void shouldRejectBookingWithNonPayableStatus() {
    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L))
        .thenReturn(Optional.of(booking("CONFIRMED", OffsetDateTime.now().plusMinutes(10))));

    AppException exception =
        assertThrows(
            AppException.class,
            () -> paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS")));

    assertEquals("BOOKING_NOT_PAYABLE", exception.getCode());
  }

  @Test
  void shouldReturnOnlySupportedMethodsWhenFiltered() {
    when(bookingRepository.findByIdAndUserId(10L, 7L))
        .thenReturn(Optional.of(booking("PENDING", OffsetDateTime.now().plusMinutes(15))));

    List<PaymentMethodView> groups = paymentService.getPaymentMethods(10L, 7L, true);

    assertEquals(1, groups.size());
    assertEquals("CARD", groups.getFirst().type());
    assertEquals(1, groups.getFirst().methods().size());
    assertEquals("PAYOS_QR", groups.getFirst().methods().getFirst().code());
    assertEquals(true, groups.getFirst().methods().getFirst().supported());
  }

  @Test
  void shouldRejectUnsupportedMethod() {
    AppException exception =
        assertThrows(
            AppException.class,
            () -> paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "STRIPE")));

    assertEquals("UNSUPPORTED_PAYMENT_METHOD", exception.getCode());
  }

  @Test
  void shouldCreatePendingPaymentAndReturnQrAction() {
    BookingEntity booking = booking("PENDING", OffsetDateTime.now().plusMinutes(15));
    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of());
    when(paymentRepository.saveAndFlush(any(PaymentEntity.class)))
        .thenAnswer(
            invocation -> {
              PaymentEntity payment = invocation.getArgument(0, PaymentEntity.class);
              payment.setId(123L);
              return payment;
            });
    when(paymentGateway.createPaymentLink(any()))
        .thenReturn(
            new PaymentGatewayResult(
                "plink_123",
                "https://pay.payos.vn/web/123",
                "000201PAYOSQR",
                "PENDING",
                "QR",
                booking.getPnrExpiresAt()));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    CreatePaymentResult result =
        paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS"));

    assertEquals(123L, result.payment().id());
    assertEquals("PAYOS", result.payment().method());
    assertEquals("PENDING", result.payment().status());
    assertEquals("plink_123", result.payment().transactionId());
    assertEquals("QR", result.action().type());
    assertEquals("https://pay.payos.vn/web/123", result.action().paymentUrl());
    ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
    verify(paymentRepository).save(captor.capture());
    assertEquals("plink_123", captor.getValue().getTransactionId());
    assertEquals("000201PAYOSQR", captor.getValue().getQrData());
  }

  @Test
  void shouldMarkPreviousPendingPaymentsAsFailedBeforeCreatingNew() {
    BookingEntity booking = booking("PENDING_PAYMENT", OffsetDateTime.now().plusMinutes(15));
    PaymentEntity stale = new PaymentEntity();
    stale.setId(99L);
    stale.setBooking(booking);
    stale.setPaymentStatus("PENDING");
    stale.setAmount(new BigDecimal("1200000"));
    stale.setCurrency("VND");

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of(stale));
    when(paymentRepository.saveAndFlush(any(PaymentEntity.class)))
        .thenAnswer(
            invocation -> {
              PaymentEntity payment = invocation.getArgument(0, PaymentEntity.class);
              payment.setId(123L);
              return payment;
            });
    when(paymentGateway.createPaymentLink(any()))
        .thenReturn(
            new PaymentGatewayResult(
                "plink_123",
                "https://pay.payos.vn/web/123",
                "000201PAYOSQR",
                "PENDING",
                "QR",
                booking.getPnrExpiresAt()));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS"));

    assertEquals("FAILED", stale.getPaymentStatus());
    verify(paymentRepository).saveAndFlush(any(PaymentEntity.class));
    verify(paymentRepository).save(any(PaymentEntity.class));
  }

  @Test
  void shouldReturnPaymentStatusForBookingOwner() {
    OffsetDateTime expireAt = OffsetDateTime.parse("2026-04-08T15:30:00Z");
    BookingEntity booking = booking("PENDING_PAYMENT", expireAt);
    booking.setId(10L);
    PaymentEntity payment = paymentForBooking(5L, booking, "PENDING");
    when(paymentRepository.findByIdAndBookingUserId(5L, 7L)).thenReturn(Optional.of(payment));

    PaymentStatusView view = paymentService.getPaymentForUser(5L, 7L);

    assertEquals(5L, view.id());
    assertEquals(10L, view.bookingId());
    assertEquals("PAYOS", view.method());
    assertEquals("PENDING", view.status());
    assertEquals(expireAt, view.expireAt());
    assertEquals("plink_x", view.transactionId());
    assertEquals("qrdata", view.qrData());
    assertEquals(null, view.paymentUrl());
  }

  @Test
  void shouldRejectPaymentStatusWhenPaymentMissing() {
    when(paymentRepository.findByIdAndBookingUserId(99L, 7L)).thenReturn(Optional.empty());

    PaymentNotFoundException exception =
        assertThrows(
            PaymentNotFoundException.class, () -> paymentService.getPaymentForUser(99L, 7L));

    assertEquals("PAYMENT_NOT_FOUND", exception.getCode());
  }

  @Test
  void shouldRejectPaymentStatusWhenWrongOwner() {
    when(paymentRepository.findByIdAndBookingUserId(5L, 7L)).thenReturn(Optional.empty());

    PaymentNotFoundException exception =
        assertThrows(
            PaymentNotFoundException.class, () -> paymentService.getPaymentForUser(5L, 7L));

    assertEquals("PAYMENT_NOT_FOUND", exception.getCode());
  }

  private static BookingEntity booking(String status, OffsetDateTime pnrExpiresAt) {
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setPnrCode("ABC123");
    booking.setStatus(status);
    booking.setCurrency("VND");
    booking.setTotalAmount(new BigDecimal("1200000"));
    booking.setContactEmail("user@example.com");
    booking.setContactPhone("0901234567");
    booking.setPnrExpiresAt(pnrExpiresAt);
    return booking;
  }

  private static PaymentEntity paymentForBooking(Long paymentId, BookingEntity booking, String status) {
    PaymentEntity payment = new PaymentEntity();
    payment.setId(paymentId);
    payment.setBooking(booking);
    payment.setAmount(new BigDecimal("1200000"));
    payment.setCurrency("VND");
    payment.setPaymentMethod("PAYOS");
    payment.setPaymentStatus(status);
    payment.setTransactionId("plink_x");
    payment.setQrData("qrdata");
    payment.setType("QR");
    payment.setCreatedAt(OffsetDateTime.parse("2026-04-01T10:00:00Z"));
    payment.setUpdatedAt(OffsetDateTime.parse("2026-04-01T10:00:00Z"));
    return payment;
  }
}
