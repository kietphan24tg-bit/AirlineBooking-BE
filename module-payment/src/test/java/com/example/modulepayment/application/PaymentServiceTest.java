package com.example.modulepayment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulebooking.entity.BookingFareHoldEntity;
import com.example.modulebooking.repository.BookingFareHoldRepository;
import com.example.modulebooking.repository.BookingServiceRepository;
import com.example.modulebooking.repository.FlightSeatRepository;
import com.example.modulebooking.repository.PassengerSeatAssignmentRepository;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.modulepayment.config.PaymentProperties;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.exception.PaymentNotFoundException;
import com.example.modulepayment.gateway.PaymentGateway;
import com.example.modulepayment.gateway.PaymentGateway.CancelPaymentLinkRequest;
import com.example.modulepayment.gateway.PaymentGateway.PaymentGatewayResult;
import com.example.modulepayment.gateway.StripePaymentGateway;
import com.example.modulepayment.repository.BookingRepository;
import com.example.modulepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
  @Mock private StripePaymentGateway stripePaymentGateway;
  @Mock private BookingFareHoldRepository bookingFareHoldRepository;
  @Mock private FlightSeatRepository flightSeatRepository;
  @Mock private BookingServiceRepository bookingServiceRepository;
  @Mock private PassengerSeatAssignmentRepository passengerSeatAssignmentRepository;

  private PaymentService paymentService;

  @BeforeEach
  void setUp() {
    paymentService =
        new PaymentService(
            bookingRepository,
            paymentRepository,
            paymentGateway,
            stripePaymentGateway,
            new PaymentProperties(
                "payos",
                "webhook-secret",
                "http://localhost:3000/payment/success",
                "http://localhost:3000/payment/cancel",
                "http://localhost:8080",
                new PaymentProperties.Payos(
                    "client-id", "api-key", "checksum-key", "https://api-merchant.payos.vn"),
                null),
            bookingFareHoldRepository,
            flightSeatRepository,
            bookingServiceRepository,
            passengerSeatAssignmentRepository);
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

    assertEquals(2, groups.size());
    assertEquals("CARD", groups.getFirst().type());
    assertEquals(1, groups.getFirst().methods().size());
    assertEquals("PAYOS_QR", groups.getFirst().methods().getFirst().code());
    assertEquals(true, groups.getFirst().methods().getFirst().supported());
    assertEquals("PAY_LATER", groups.get(1).type());
    assertEquals("PAY_LATER", groups.get(1).methods().getFirst().code());
    assertEquals(true, groups.get(1).methods().getFirst().supported());
    assertEquals(60, groups.get(1).methods().getFirst().meta().holdDurationMinutes());
  }

  @Test
  void shouldHideImmediatePaymentMethodsWhenRemainingWindowIsTooShort() {
    PaymentService stripeService = paymentService(stripeProperties());
    BookingEntity booking = booking("PENDING_PAYMENT", OffsetDateTime.now().plusMinutes(4));
    booking.setPaymentWindowExtendedAt(OffsetDateTime.now().minusMinutes(1));
    when(bookingRepository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(booking));

    List<PaymentMethodView> groups = stripeService.getPaymentMethods(10L, 7L, true);

    assertEquals(2, groups.size());
    assertEquals("CARD", groups.getFirst().type());
    assertEquals("STRIPE_CARD", groups.getFirst().methods().getFirst().code());
    assertEquals("PAY_LATER", groups.get(1).type());
    assertEquals("PAY_LATER", groups.get(1).methods().getFirst().code());
  }

  @Test
  void shouldCreateStripeCheckoutPayment() {
    PaymentService stripeService = paymentService(stripeProperties());
    BookingEntity booking = booking("PENDING", OffsetDateTime.now().plusMinutes(35));
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
    when(stripePaymentGateway.createCheckoutSession(any()))
        .thenReturn(
            new PaymentGatewayResult(
                "cs_test_123",
                "https://checkout.stripe.com/c/pay/cs_test_123",
                null,
                "open",
                "CHECKOUT",
                booking.getPnrExpiresAt()));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    CreatePaymentResult result =
        stripeService.createPayment(new CreatePaymentCommand(10L, 7L, "STRIPE_CARD"));

    assertEquals("STRIPE_CARD", result.payment().method());
    assertEquals("cs_test_123", result.payment().transactionId());
    assertEquals("CHECKOUT", result.action().type());
    assertEquals("https://checkout.stripe.com/c/pay/cs_test_123", result.action().paymentUrl());

    ArgumentCaptor<PaymentGateway.PaymentGatewayRequest> requestCaptor =
        ArgumentCaptor.forClass(PaymentGateway.PaymentGatewayRequest.class);
    verify(stripePaymentGateway).createCheckoutSession(requestCaptor.capture());
    assertEquals("Airline booking", requestCaptor.getValue().productName());
    assertEquals("PNR ABC123", requestCaptor.getValue().productDescription());
    assertEquals(4, requestCaptor.getValue().lineItems().size());
    assertEquals("Airline booking", requestCaptor.getValue().lineItems().getFirst().name());
    assertEquals(
        0,
        new BigDecimal("1200000.00")
            .compareTo(requestCaptor.getValue().lineItems().getFirst().amount()));
  }

  @Test
  void shouldExtendBookingWindowToThirtyMinutesWhenCreatingStripePaymentLate() {
    PaymentService stripeService = paymentService(stripeProperties());
    OffsetDateTime start = OffsetDateTime.now();
    BookingEntity booking = booking("PENDING", start.plusMinutes(3));

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of());
    when(paymentRepository.saveAndFlush(any(PaymentEntity.class)))
        .thenAnswer(
            invocation -> {
              PaymentEntity payment = invocation.getArgument(0, PaymentEntity.class);
              payment.setId(321L);
              return payment;
            });
    when(stripePaymentGateway.createCheckoutSession(any()))
        .thenReturn(
            new PaymentGatewayResult(
                "cs_test_321",
                "https://checkout.stripe.com/c/pay/cs_test_321",
                null,
                "open",
                "CHECKOUT",
                booking.getPnrExpiresAt()));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    CreatePaymentResult result =
        stripeService.createPayment(new CreatePaymentCommand(10L, 7L, "STRIPE_CARD"));

    assertEquals("STRIPE_CARD", result.payment().method());
    assertEquals(true, booking.getPnrExpiresAt().isAfter(start.plusMinutes(29)));
    assertEquals(booking.getPnrExpiresAt(), result.payment().expireAt());
    verify(bookingFareHoldRepository)
        .extendHeldExpiryByBookingId(
            org.mockito.ArgumentMatchers.eq(10L),
            org.mockito.ArgumentMatchers.eq(booking.getPnrExpiresAt()),
            any());
    verify(flightSeatRepository).extendReservedUntilForBooking(10L, booking.getPnrExpiresAt());
  }

  @Test
  void shouldRejectUnsupportedMethod() {
    AppException exception =
        assertThrows(
            AppException.class,
            () -> paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "MOMO")));

    assertEquals("UNSUPPORTED_PAYMENT_METHOD", exception.getCode());
  }

  @Test
  void shouldCancelPendingPaymentAndCallGateway() {
    BookingEntity booking = booking("PENDING_PAYMENT", OffsetDateTime.now().plusMinutes(15));
    PaymentEntity payment = paymentForBooking(5L, booking, "PENDING");
    when(paymentRepository.findByIdAndBookingUserIdForUpdate(5L, 7L))
        .thenReturn(Optional.of(payment));
    doNothing().when(paymentGateway).cancelPaymentLink(any(CancelPaymentLinkRequest.class));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    PaymentStatusView view = paymentService.cancelPaymentForUser(5L, 7L, "Changed my mind");

    assertEquals("CANCELLED", view.status());
    ArgumentCaptor<CancelPaymentLinkRequest> captor =
        ArgumentCaptor.forClass(CancelPaymentLinkRequest.class);
    verify(paymentGateway).cancelPaymentLink(captor.capture());
    assertEquals(5L, captor.getValue().orderCode());
    assertEquals("Changed my mind", captor.getValue().cancellationReason());
  }

  @Test
  void shouldBeIdempotentWhenCancellingAlreadyCancelledPayment() {
    BookingEntity booking = booking("PENDING_PAYMENT", OffsetDateTime.now().plusMinutes(15));
    PaymentEntity payment = paymentForBooking(5L, booking, "CANCELLED");
    when(paymentRepository.findByIdAndBookingUserIdForUpdate(5L, 7L))
        .thenReturn(Optional.of(payment));

    PaymentStatusView view = paymentService.cancelPaymentForUser(5L, 7L, "any");

    assertEquals("CANCELLED", view.status());
    verify(paymentGateway, never()).cancelPaymentLink(any());
    verify(paymentRepository, never()).save(any());
  }

  @Test
  void shouldRejectCancelWhenPaymentNotPending() {
    BookingEntity booking = booking("CONFIRMED", OffsetDateTime.now().plusMinutes(15));
    PaymentEntity payment = paymentForBooking(5L, booking, "SUCCESS");
    when(paymentRepository.findByIdAndBookingUserIdForUpdate(5L, 7L))
        .thenReturn(Optional.of(payment));

    AppException exception =
        assertThrows(AppException.class, () -> paymentService.cancelPaymentForUser(5L, 7L, null));

    assertEquals("PAYMENT_NOT_CANCELLABLE", exception.getCode());
    verify(paymentGateway, never()).cancelPaymentLink(any());
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
    assertEquals("PENDING_PAYMENT", booking.getStatus());
    ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
    verify(paymentRepository).save(captor.capture());
    assertEquals("plink_123", captor.getValue().getTransactionId());
    assertEquals("000201PAYOSQR", captor.getValue().getQrData());
    assertEquals("https://pay.payos.vn/web/123", captor.getValue().getCheckoutUrl());
  }

  @Test
  void shouldCreatePayLaterPaymentWithOneHourHold() {
    OffsetDateTime originalExpireAt = OffsetDateTime.now().plusMinutes(15);
    BookingEntity booking = booking("PENDING", originalExpireAt);
    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of());
    when(paymentRepository.saveAndFlush(any(PaymentEntity.class)))
        .thenAnswer(
            invocation -> {
              PaymentEntity payment = invocation.getArgument(0, PaymentEntity.class);
              payment.setId(456L);
              return payment;
            });
    when(paymentGateway.createPaymentLink(any()))
        .thenReturn(
            new PaymentGatewayResult(
                "plink_456",
                "https://pay.payos.vn/web/456",
                "000201PAYLATERQR",
                "PENDING",
                "QR",
                booking.getPnrExpiresAt()));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    CreatePaymentResult result =
        paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAY_LATER"));

    assertEquals("PAY_LATER", result.payment().method());
    assertEquals("PENDING_PAYMENT", booking.getStatus());
    assertEquals(true, result.payment().expireAt().isAfter(originalExpireAt.plusMinutes(40)));
    verify(bookingFareHoldRepository)
        .extendHeldExpiryByBookingId(
            org.mockito.ArgumentMatchers.eq(10L),
            org.mockito.ArgumentMatchers.eq(booking.getPnrExpiresAt()),
            any());
    verify(flightSeatRepository).extendReservedUntilForBooking(10L, booking.getPnrExpiresAt());
  }

  @Test
  void shouldReusePayLaterPaymentWithoutRefreshingHoldWindow() {
    OffsetDateTime originalExpireAt = OffsetDateTime.now().plusMinutes(15);
    BookingEntity booking = booking("PENDING_PAYMENT", originalExpireAt);
    PaymentEntity existing = paymentForBooking(456L, booking, "PENDING");
    existing.setPaymentMethod("PAY_LATER");
    existing.setTransactionId("plink_456");
    existing.setType("QR");
    existing.setCheckoutUrl("https://pay.payos.vn/web/456");
    existing.setCreatedAt(OffsetDateTime.now().minusMinutes(1));

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of(existing));
    doNothing().when(paymentGateway).cancelPaymentLink(any(CancelPaymentLinkRequest.class));
    when(paymentRepository.saveAndFlush(any(PaymentEntity.class)))
        .thenAnswer(
            invocation -> {
              PaymentEntity payment = invocation.getArgument(0, PaymentEntity.class);
              payment.setId(789L);
              return payment;
            });
    when(paymentGateway.createPaymentLink(any()))
        .thenReturn(
            new PaymentGatewayResult(
                "plink_789",
                "https://pay.payos.vn/web/789",
                "000201PAYLATERQR",
                "PENDING",
                "QR",
                booking.getPnrExpiresAt()));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    CreatePaymentResult result =
        paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAY_LATER"));

    assertEquals(789L, result.payment().id());
    assertEquals("PAY_LATER", result.payment().method());
    assertEquals(booking.getPnrExpiresAt(), result.payment().expireAt());
    assertEquals("CANCELLED", existing.getPaymentStatus());
    verify(paymentGateway).cancelPaymentLink(any(CancelPaymentLinkRequest.class));
    verify(bookingFareHoldRepository).extendHeldExpiryByBookingId(any(), any(), any());
    verify(flightSeatRepository).extendReservedUntilForBooking(any(), any());
  }

  @Test
  void shouldCreatePendingPaymentPublicly() {
    BookingEntity booking = booking("PENDING", OffsetDateTime.now().plusMinutes(15));
    when(bookingRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(booking));
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
        paymentService.createPaymentPublic(new CreatePaymentCommand(10L, null, "PAYOS"));

    assertEquals(123L, result.payment().id());
    assertEquals("PENDING_PAYMENT", booking.getStatus());
  }

  @Test
  void shouldExtendBookingWindowByFiveMinutesWhenStartingPaymentLate() {
    OffsetDateTime start = OffsetDateTime.now();
    BookingEntity booking = booking("PENDING", start.plusMinutes(2)); // < 5 minutes remaining

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

    assertEquals(true, booking.getPnrExpiresAt().isAfter(start.plusMinutes(4)));
    assertEquals(true, booking.getPnrExpiresAt().isBefore(start.plusMinutes(6)));
    assertEquals(true, booking.getPaymentWindowExtendedAt() != null);
    assertEquals(booking.getPnrExpiresAt(), result.payment().expireAt());
    verify(bookingFareHoldRepository)
        .extendHeldExpiryByBookingId(
            10L, booking.getPnrExpiresAt(), booking.getPaymentWindowExtendedAt());
    verify(flightSeatRepository).extendReservedUntilForBooking(10L, booking.getPnrExpiresAt());
  }

  @Test
  void shouldReusePendingPaymentWithoutExtendingAgainWhenAlreadyExtendedAndStillHasEnoughTime() {
    OffsetDateTime start = OffsetDateTime.now();
    BookingEntity booking = booking("PENDING", start.plusMinutes(6));
    OffsetDateTime previousExtendedAt = start.minusMinutes(2);
    booking.setPaymentWindowExtendedAt(previousExtendedAt);

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));

    PaymentEntity existing = paymentForBooking(5L, booking, "PENDING");
    existing.setCheckoutUrl("https://pay.payos.vn/web/5");
    existing.setCreatedAt(OffsetDateTime.now().minusMinutes(1));

    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of(existing));

    CreatePaymentResult result =
        paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS"));

    assertEquals(5L, result.payment().id());
    assertEquals(start.plusMinutes(6), booking.getPnrExpiresAt());
    assertEquals(booking.getPnrExpiresAt(), result.payment().expireAt());
    assertEquals(previousExtendedAt, booking.getPaymentWindowExtendedAt());
    verify(paymentGateway, never()).createPaymentLink(any());
    verify(bookingFareHoldRepository, never()).extendHeldExpiryByBookingId(any(), any(), any());
    verify(flightSeatRepository, never()).extendReservedUntilForBooking(any(), any());
  }

  @Test
  void shouldRejectQrPaymentWhenRemainingWindowIsShortEvenAfterExtensionWasUsed() {
    OffsetDateTime start = OffsetDateTime.now();
    BookingEntity booking = booking("PENDING_PAYMENT", start.plusMinutes(3));
    booking.setPaymentWindowExtendedAt(start.minusMinutes(1));
    booking.setCreatedAt(start.minusMinutes(12));
    booking.setUpdatedAt(start.minusMinutes(12));

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of());

    AppException exception =
        assertThrows(
            AppException.class,
            () -> paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS")));

    assertEquals("PAYMENT_WINDOW_TOO_SHORT", exception.getCode());
    verify(paymentRepository, never()).saveAndFlush(any(PaymentEntity.class));
    verify(paymentGateway, never()).createPaymentLink(any());
  }

  @Test
  void shouldRejectRefreshingQrPaymentWhenRemainingWindowIsShortEvenAfterExtensionWasUsed() {
    OffsetDateTime start = OffsetDateTime.now();
    BookingEntity booking = booking("PENDING_PAYMENT", start.plusMinutes(3));
    booking.setPaymentWindowExtendedAt(start.minusMinutes(1));
    booking.setCreatedAt(start.minusMinutes(12));
    booking.setUpdatedAt(start.minusMinutes(12));
    PaymentEntity existing = paymentForBooking(5L, booking, "PENDING");
    existing.setCheckoutUrl("https://pay.payos.vn/web/5");
    existing.setCreatedAt(start.minusMinutes(1));

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of(existing));

    AppException exception =
        assertThrows(
            AppException.class,
            () -> paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS")));

    assertEquals("PAYMENT_WINDOW_TOO_SHORT", exception.getCode());
    assertEquals("PENDING", existing.getPaymentStatus());
    verify(paymentGateway, never()).cancelPaymentLink(any(CancelPaymentLinkRequest.class));
    verify(paymentRepository, never()).saveAndFlush(any(PaymentEntity.class));
    verify(paymentGateway, never()).createPaymentLink(any());
  }

  @Test
  void shouldReuseExistingPendingPaymentWhenStillValid() {
    OffsetDateTime expireAt = OffsetDateTime.now().plusMinutes(15);
    BookingEntity booking = booking("PENDING", expireAt);
    PaymentEntity existing = paymentForBooking(5L, booking, "PENDING");
    existing.setCheckoutUrl("https://pay.payos.vn/web/5");
    existing.setCreatedAt(OffsetDateTime.now().minusMinutes(1));

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of(existing));

    CreatePaymentResult result =
        paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS"));

    assertEquals(5L, result.payment().id());
    assertEquals("PENDING", result.payment().status());
    assertEquals("https://pay.payos.vn/web/5", result.action().paymentUrl());
    assertEquals("PENDING_PAYMENT", booking.getStatus());
    verify(paymentGateway, never()).createPaymentLink(any());
    verify(paymentRepository, never())
        .findByBookingIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any());
  }

  @Test
  void shouldRejectPaymentCreationDuringCooldown() {
    OffsetDateTime expireAt = OffsetDateTime.now().plusMinutes(15);
    BookingEntity booking = booking("PENDING_PAYMENT", expireAt);
    PaymentEntity recent = paymentForBooking(9L, booking, "FAILED");
    recent.setCreatedAt(OffsetDateTime.now().minusSeconds(5));

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of());
    when(paymentRepository.findByBookingIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            any(), any()))
        .thenReturn(List.of(recent));

    AppException exception =
        assertThrows(
            AppException.class,
            () -> paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS")));

    assertEquals("PAYMENT_CREATION_COOLDOWN", exception.getCode());
    verify(paymentRepository, never()).saveAndFlush(any(PaymentEntity.class));
    verify(paymentGateway, never()).createPaymentLink(any());
  }

  @Test
  void shouldAllowSwitchingPaymentMethodDuringCooldownWindow() {
    OffsetDateTime expireAt = OffsetDateTime.now().plusMinutes(15);
    BookingEntity booking = booking("PENDING_PAYMENT", expireAt);
    PaymentEntity recentPayLater = paymentForBooking(9L, booking, "FAILED");
    recentPayLater.setPaymentMethod("PAY_LATER");
    recentPayLater.setCreatedAt(OffsetDateTime.now().minusSeconds(5));

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of());
    when(paymentRepository.findByBookingIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            any(), any()))
        .thenReturn(List.of(recentPayLater));
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
                expireAt));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    CreatePaymentResult result =
        paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS"));

    assertEquals("PAYOS", result.payment().method());
    assertEquals(123L, result.payment().id());
    verify(paymentRepository).saveAndFlush(any(PaymentEntity.class));
    verify(paymentGateway).createPaymentLink(any());
  }

  @Test
  void shouldRejectPaymentCreationWhenRateLimitExceeded() {
    OffsetDateTime expireAt = OffsetDateTime.now().plusMinutes(15);
    BookingEntity booking = booking("PENDING_PAYMENT", expireAt);
    PaymentEntity first = paymentForBooking(11L, booking, "FAILED");
    first.setCreatedAt(OffsetDateTime.now().minusSeconds(20));
    PaymentEntity second = paymentForBooking(12L, booking, "FAILED");
    second.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
    PaymentEntity third = paymentForBooking(13L, booking, "FAILED");
    third.setCreatedAt(OffsetDateTime.now().minusMinutes(2));
    PaymentEntity fourth = paymentForBooking(14L, booking, "FAILED");
    fourth.setCreatedAt(OffsetDateTime.now().minusMinutes(3));
    PaymentEntity fifth = paymentForBooking(15L, booking, "FAILED");
    fifth.setCreatedAt(OffsetDateTime.now().minusMinutes(4));

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of());
    when(paymentRepository.findByBookingIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            any(), any()))
        .thenReturn(List.of(first, second, third, fourth, fifth));

    AppException exception =
        assertThrows(
            AppException.class,
            () -> paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS")));

    assertEquals("PAYMENT_CREATION_LIMIT_EXCEEDED", exception.getCode());
    verify(paymentRepository, never()).saveAndFlush(any(PaymentEntity.class));
    verify(paymentGateway, never()).createPaymentLink(any());
  }

  @Test
  void shouldCancelPayosPendingPaymentWhenSwitchingToStripe() {
    PaymentService stripeService = paymentService(stripeProperties());
    OffsetDateTime expireAt = OffsetDateTime.now().plusMinutes(15);
    BookingEntity booking = booking("PENDING_PAYMENT", expireAt);
    PaymentEntity existing = paymentForBooking(5L, booking, "PENDING");
    existing.setCreatedAt(OffsetDateTime.now().minusMinutes(1));

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of(existing));
    doNothing().when(paymentGateway).cancelPaymentLink(any(CancelPaymentLinkRequest.class));
    when(paymentRepository.saveAndFlush(any(PaymentEntity.class)))
        .thenAnswer(
            invocation -> {
              PaymentEntity payment = invocation.getArgument(0, PaymentEntity.class);
              payment.setId(123L);
              return payment;
            });
    when(stripePaymentGateway.createCheckoutSession(any()))
        .thenReturn(
            new PaymentGatewayResult(
                "cs_test_123",
                "https://checkout.stripe.com/c/pay/cs_test_123",
                null,
                "open",
                "CHECKOUT",
                expireAt));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    CreatePaymentResult result =
        stripeService.createPayment(new CreatePaymentCommand(10L, 7L, "STRIPE_CARD"));

    assertEquals("CANCELLED", existing.getPaymentStatus());
    assertEquals("STRIPE_CARD", result.payment().method());
    assertEquals(123L, result.payment().id());
    assertEquals(booking.getPnrExpiresAt(), result.payment().expireAt());
    assertEquals(true, booking.getPnrExpiresAt().isAfter(expireAt.plusMinutes(10)));
    verify(paymentGateway).cancelPaymentLink(any(CancelPaymentLinkRequest.class));
    verify(stripePaymentGateway).createCheckoutSession(any());
  }

  @Test
  void shouldExpireStripePendingPaymentWhenSwitchingToPayos() {
    OffsetDateTime expireAt = OffsetDateTime.now().plusMinutes(15);
    BookingEntity booking = booking("PENDING_PAYMENT", expireAt);
    PaymentEntity existing = paymentForBooking(5L, booking, "PENDING");
    existing.setPaymentMethod("STRIPE_CARD");
    existing.setTransactionId("cs_old_123");
    existing.setType("CHECKOUT");
    existing.setCheckoutUrl("https://checkout.stripe.com/c/pay/cs_old_123");
    existing.setCreatedAt(OffsetDateTime.now().minusMinutes(1));

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of(existing));
    doNothing().when(stripePaymentGateway).expireCheckoutSession("cs_old_123");
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
                expireAt));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    CreatePaymentResult result =
        paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS"));

    assertEquals("CANCELLED", existing.getPaymentStatus());
    assertEquals("PAYOS", result.payment().method());
    assertEquals(123L, result.payment().id());
    assertEquals(booking.getPnrExpiresAt(), result.payment().expireAt());
    assertEquals(true, booking.getPnrExpiresAt().isBefore(expireAt));
    verify(stripePaymentGateway).expireCheckoutSession("cs_old_123");
    verify(paymentGateway).createPaymentLink(any());
  }

  @Test
  void shouldShrinkPayLaterWindowWhenSwitchingToPayos() {
    OffsetDateTime payLaterExpireAt = OffsetDateTime.now().plusMinutes(60);
    BookingEntity booking = booking("PENDING_PAYMENT", payLaterExpireAt);
    PaymentEntity existing = paymentForBooking(5L, booking, "PENDING");
    existing.setPaymentMethod("PAY_LATER");
    existing.setTransactionId("plink_old_123");
    existing.setType("QR");
    existing.setCheckoutUrl("https://pay.payos.vn/web/5");
    existing.setCreatedAt(OffsetDateTime.now().minusMinutes(1));

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of(existing));
    doNothing().when(paymentGateway).cancelPaymentLink(any(CancelPaymentLinkRequest.class));
    when(paymentRepository.saveAndFlush(any(PaymentEntity.class)))
        .thenAnswer(
            invocation -> {
              PaymentEntity payment = invocation.getArgument(0, PaymentEntity.class);
              payment.setId(123L);
              return payment;
            });
    when(paymentGateway.createPaymentLink(any()))
        .thenAnswer(
            invocation ->
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

    assertEquals("CANCELLED", existing.getPaymentStatus());
    assertEquals("PAYOS", result.payment().method());
    assertEquals(booking.getPnrExpiresAt(), result.payment().expireAt());
    assertEquals(true, booking.getPnrExpiresAt().isBefore(payLaterExpireAt.minusMinutes(50)));
    verify(bookingFareHoldRepository)
        .extendHeldExpiryByBookingId(10L, booking.getPnrExpiresAt(), booking.getUpdatedAt());
    verify(flightSeatRepository).extendReservedUntilForBooking(10L, booking.getPnrExpiresAt());
  }

  @Test
  void shouldNotCreateNewPaymentWhenCancellingPreviousMethodFails() {
    PaymentService stripeService = paymentService(stripeProperties());
    BookingEntity booking = booking("PENDING_PAYMENT", OffsetDateTime.now().plusMinutes(15));
    PaymentEntity existing = paymentForBooking(5L, booking, "PENDING");

    when(bookingRepository.findByIdAndUserIdForUpdate(10L, 7L)).thenReturn(Optional.of(booking));
    when(paymentRepository.findAllByBookingIdAndPaymentStatusForUpdate(10L, "PENDING"))
        .thenReturn(List.of(existing));
    doThrow(new RuntimeException("cancel failed"))
        .when(paymentGateway)
        .cancelPaymentLink(any(CancelPaymentLinkRequest.class));

    assertThrows(
        RuntimeException.class,
        () -> stripeService.createPayment(new CreatePaymentCommand(10L, 7L, "STRIPE_CARD")));

    assertEquals("PENDING", existing.getPaymentStatus());
    verify(paymentRepository, never()).saveAndFlush(any(PaymentEntity.class));
    verify(stripePaymentGateway, never()).createCheckoutSession(any());
  }

  @Test
  void shouldMarkPaymentFailedWhenProviderThrows() {
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
    when(paymentGateway.createPaymentLink(any())).thenThrow(new RuntimeException("boom"));
    when(paymentRepository.save(any(PaymentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, PaymentEntity.class));

    assertThrows(
        RuntimeException.class,
        () -> paymentService.createPayment(new CreatePaymentCommand(10L, 7L, "PAYOS")));

    ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
    verify(paymentRepository).save(captor.capture());
    assertEquals("FAILED", captor.getValue().getPaymentStatus());
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
    assertEquals("https://pay.payos.vn/web/5", view.paymentUrl());
  }

  @Test
  void shouldReturnPaymentDetailsForStatus() {
    OffsetDateTime expireAt = OffsetDateTime.parse("2026-04-08T15:30:00Z");
    BookingEntity booking = booking("PENDING_PAYMENT", expireAt);
    booking.setId(10L);
    booking.setDiscountAmount(new BigDecimal("50000"));
    PaymentEntity payment = paymentForBooking(5L, booking, "PENDING");
    BookingFareHoldEntity hold = fareHold(booking);

    when(paymentRepository.findByIdAndBookingUserId(5L, 7L)).thenReturn(Optional.of(payment));
    when(bookingFareHoldRepository.findFirstByBookingIdAndStatusIn(
            10L, List.of("HELD", "CONFIRMED")))
        .thenReturn(Optional.of(hold));
    when(bookingServiceRepository.sumPriceByBookingId(10L)).thenReturn(new BigDecimal("100000"));
    when(passengerSeatAssignmentRepository.sumActiveSurchargeByBookingId(10L))
        .thenReturn(new BigDecimal("50000"));

    PaymentStatusView view = paymentService.getPaymentForUser(5L, 7L);

    assertEquals("VN123", view.details().flight().flightNumber());
    assertEquals("HAN", view.details().flight().departureCode());
    assertEquals("SGN", view.details().flight().arrivalCode());
    assertEquals(new BigDecimal("1000000.00"), view.details().price().baseFare());
    assertEquals(new BigDecimal("100000.00"), view.details().price().tax());
    assertEquals(new BigDecimal("50000.00"), view.details().price().discount());
    assertEquals(new BigDecimal("100000.00"), view.details().price().serviceFee());
    assertEquals(new BigDecimal("50000.00"), view.details().price().seatFee());
    assertEquals(new BigDecimal("1200000.00"), view.details().price().total());
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
    OffsetDateTime createdAt = OffsetDateTime.now();
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setPnrCode("ABC123");
    booking.setStatus(status);
    booking.setCurrency("VND");
    booking.setTotalAmount(new BigDecimal("1200000"));
    booking.setDiscountAmount(BigDecimal.ZERO);
    booking.setContactEmail("user@example.com");
    booking.setContactPhone("0901234567");
    booking.setPnrExpiresAt(pnrExpiresAt);
    booking.setCreatedAt(createdAt);
    booking.setUpdatedAt(createdAt);
    return booking;
  }

  private PaymentService paymentService(PaymentProperties properties) {
    return new PaymentService(
        bookingRepository,
        paymentRepository,
        paymentGateway,
        stripePaymentGateway,
        properties,
        bookingFareHoldRepository,
        flightSeatRepository,
        bookingServiceRepository,
        passengerSeatAssignmentRepository);
  }

  private static PaymentProperties stripeProperties() {
    return new PaymentProperties(
        "stripe",
        "webhook-secret",
        "http://localhost:3000/payment/success",
        "http://localhost:3000/payment/cancel",
        "http://localhost:8080",
        new PaymentProperties.Payos(
            "client-id", "api-key", "checksum-key", "https://api-merchant.payos.vn"),
        new PaymentProperties.Stripe("sk_test_123", "whsec_123", "2026-02-25.clover"));
  }

  private static BookingFareHoldEntity fareHold(BookingEntity booking) {
    AirlineEntity airline = new AirlineEntity();
    airline.setCode("VN");
    airline.setName("Vietnam Airlines");

    AirportEntity departure = new AirportEntity();
    departure.setCode("HAN");
    departure.setCity("Hanoi");
    departure.setName("Noi Bai International Airport");

    AirportEntity arrival = new AirportEntity();
    arrival.setCode("SGN");
    arrival.setCity("Ho Chi Minh City");
    arrival.setName("Tan Son Nhat International Airport");

    FlightEntity flight = new FlightEntity();
    flight.setAirline(airline);
    flight.setFlightNumber("VN123");
    flight.setDepartureAirport(departure);
    flight.setArrivalAirport(arrival);
    flight.setFlightDate(LocalDate.parse("2026-05-01"));
    flight.setDepartureTime(OffsetDateTime.parse("2026-05-01T02:00:00Z"));
    flight.setArrivalTime(OffsetDateTime.parse("2026-05-01T04:00:00Z"));

    FlightFareEntity fare = new FlightFareEntity();
    fare.setFlight(flight);
    fare.setBasePrice(new BigDecimal("1000000"));
    fare.setTotalTaxAmount(new BigDecimal("100000"));

    BookingFareHoldEntity hold = new BookingFareHoldEntity();
    hold.setBooking(booking);
    hold.setFlightFare(fare);
    hold.setQuantity(1);
    hold.setStatus("HELD");
    return hold;
  }

  private static PaymentEntity paymentForBooking(
      Long paymentId, BookingEntity booking, String status) {
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
    payment.setCheckoutUrl("https://pay.payos.vn/web/" + paymentId);
    payment.setCreatedAt(OffsetDateTime.parse("2026-04-01T10:00:00Z"));
    payment.setUpdatedAt(OffsetDateTime.parse("2026-04-01T10:00:00Z"));
    return payment;
  }
}
