package com.example.appbootstrap.controller.payment;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.example.appbootstrap.controller.payment.dto.PaymentWebhookResponse;
import com.example.modulepayment.application.PaymentWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PaymentWebhooksControllerTest {

  @Mock private PaymentWebhookService paymentWebhookService;

  private PaymentWebhooksController controller;

  @BeforeEach
  void setUp() {
    controller = new PaymentWebhooksController(paymentWebhookService);
  }

  @Test
  void shouldReturnReceivedAckForPayosWebhook() {
    PaymentWebhookResponse response = controller.handlePayosWebhook("{\"signature\":\"invalid\"}");

    assertTrue(response.received());
    verify(paymentWebhookService).handlePayosWebhook("{\"signature\":\"invalid\"}");
  }

  @Test
  void shouldAckWebhookWhenIdempotencyRowConflict() {
    doThrow(new DataIntegrityViolationException("duplicate idempotency"))
        .when(paymentWebhookService)
        .handlePayosWebhook("payload");

    PaymentWebhookResponse response = controller.handlePayosWebhook("payload");

    assertTrue(response.received());
    verify(paymentWebhookService).handlePayosWebhook("payload");
  }
}
