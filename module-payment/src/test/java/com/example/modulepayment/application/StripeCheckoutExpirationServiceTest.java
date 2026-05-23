package com.example.modulepayment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.gateway.StripePaymentGateway;
import com.example.modulepayment.repository.PaymentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StripeCheckoutExpirationServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private StripePaymentGateway stripePaymentGateway;

  @InjectMocks private StripeCheckoutExpirationService service;

  @Test
  void shouldExpireStripeSessionsPastBookingCap() {
    PaymentEntity payment = stripePayment("cs_test_123");

    when(paymentRepository.findPendingStripeCardPaymentsPastBookingExpiry(any()))
        .thenReturn(List.of(payment));

    int expired = service.expireCheckoutSessionsPastBookingCap(OffsetDateTime.now());

    assertEquals(1, expired);
    verify(stripePaymentGateway).expireCheckoutSession("cs_test_123");
  }

  @Test
  void shouldContinueWhenStripeRejectsExpiration() {
    PaymentEntity payment = stripePayment("cs_test_456");

    when(paymentRepository.findPendingStripeCardPaymentsPastBookingExpiry(any()))
        .thenReturn(List.of(payment));
    doThrow(new AppException("PAYMENT_PROVIDER_ERROR", "already expired", 502))
        .when(stripePaymentGateway)
        .expireCheckoutSession("cs_test_456");

    int expired = service.expireCheckoutSessionsPastBookingCap(OffsetDateTime.now());

    assertEquals(0, expired);
    verify(stripePaymentGateway).expireCheckoutSession("cs_test_456");
  }

  @Test
  void shouldSkipWhenNoPendingStripePayments() {
    when(paymentRepository.findPendingStripeCardPaymentsPastBookingExpiry(any()))
        .thenReturn(List.of());

    int expired = service.expireCheckoutSessionsPastBookingCap(OffsetDateTime.now());

    assertEquals(0, expired);
    verify(stripePaymentGateway, never()).expireCheckoutSession(any());
  }

  private static PaymentEntity stripePayment(String sessionId) {
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    booking.setPnrExpiresAt(OffsetDateTime.now().minusMinutes(1));

    PaymentEntity payment = new PaymentEntity();
    payment.setId(5L);
    payment.setBooking(booking);
    payment.setPaymentMethod("STRIPE_CARD");
    payment.setPaymentStatus("PENDING");
    payment.setTransactionId(sessionId);
    return payment;
  }
}
