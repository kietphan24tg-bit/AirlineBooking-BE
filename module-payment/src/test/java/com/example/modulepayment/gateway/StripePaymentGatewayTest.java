package com.example.modulepayment.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.common.exception.AppException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StripePaymentGatewayTest {

  @Test
  void shouldUseZeroDecimalAmountForVnd() {
    assertEquals(1200000L, StripePaymentGateway.toStripeAmount(new BigDecimal("1200000"), "VND"));
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
