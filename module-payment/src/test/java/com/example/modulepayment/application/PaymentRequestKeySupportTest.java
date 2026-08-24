package com.example.modulepayment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.modulebooking.entity.BookingEntity;
import com.example.modulepayment.entity.PaymentEntity;
import com.example.modulepayment.exception.PaymentExecutionUnknownException;
import com.example.modulepayment.repository.PaymentRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentRequestKeySupportTest {

  @Test
  void shouldFindExistingPaymentByTrustedRequestKey() {
    PaymentRepository repository = mock(PaymentRepository.class);
    PaymentEntity payment = payment("PENDING", "PAYOS");
    when(repository.findByRequestKeyWithBookingForUpdate("ai-proposal:123"))
        .thenReturn(Optional.of(payment));

    PaymentEntity result = PaymentRequestKeySupport.findExisting(repository, "ai-proposal:123");

    assertEquals(payment, result);
  }

  @Test
  void shouldRejectRequestKeyReuseForDifferentPayment() {
    PaymentEntity payment = payment("PENDING", "PAYOS");

    AppException exception =
        assertThrows(
            AppException.class,
            () -> PaymentRequestKeySupport.validateExisting(payment, 99L, "PAYOS", true));

    assertEquals("PAYMENT_IDEMPOTENCY_KEY_CONFLICT", exception.getCode());
  }

  @Test
  void shouldKeepUnknownProviderResultFromBeingExecutedAgain() {
    PaymentEntity payment = payment("UNKNOWN", "STRIPE_CARD");

    assertThrows(
        PaymentExecutionUnknownException.class,
        () -> PaymentRequestKeySupport.validateExisting(payment, 10L, "STRIPE_CARD", true));
  }

  private PaymentEntity payment(String status, String method) {
    BookingEntity booking = new BookingEntity();
    booking.setId(10L);
    PaymentEntity payment = new PaymentEntity();
    payment.setId(20L);
    payment.setBooking(booking);
    payment.setPaymentStatus(status);
    payment.setPaymentMethod(method);
    return payment;
  }
}
