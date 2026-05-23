package com.example.modulepayment.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.common.exception.AppException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class StripePaymentGatewayTest {

  @Test
  void shouldUseZeroDecimalAmountForVnd() {
    assertEquals(1200000L, StripePaymentGateway.toStripeAmount(new BigDecimal("1200000"), "VND"));
  }

  @Test
  void checkoutExpiresAt_shouldUseCapWhenAtLeastThirtyMinutesAhead() {
    OffsetDateTime cap = OffsetDateTime.now().plusMinutes(45);

    Long stripeExpiresAt = StripePaymentGateway.checkoutExpiresAt(cap);

    assertEquals(cap.toEpochSecond(), stripeExpiresAt);
  }

  @Test
  void checkoutExpiresAt_shouldClampToStripeMinimumWhenCapIsSooner() {
    OffsetDateTime cap = OffsetDateTime.now().plusMinutes(5);
    long expectedMin =
        OffsetDateTime.now().toEpochSecond() + StripePaymentGateway.STRIPE_MIN_CHECKOUT_TTL_SECONDS;

    Long stripeExpiresAt = StripePaymentGateway.checkoutExpiresAt(cap);

    assertEquals(expectedMin, stripeExpiresAt);
  }

  @Test
  void checkoutExpiresAt_shouldReturnNullWhenCapAlreadyElapsed() {
    OffsetDateTime cap = OffsetDateTime.now().minusMinutes(1);

    assertNull(StripePaymentGateway.checkoutExpiresAt(cap));
  }

  @Test
  void shouldRejectFractionalVndAmount() {
    AppException exception =
        assertThrows(
            AppException.class,
            () -> StripePaymentGateway.toStripeAmount(new BigDecimal("1200000.50"), "VND"));

    assertEquals("INVALID_PAYMENT_AMOUNT", exception.getCode());
  }
}
