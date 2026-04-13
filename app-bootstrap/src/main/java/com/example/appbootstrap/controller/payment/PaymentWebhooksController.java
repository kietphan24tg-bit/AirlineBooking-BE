package com.example.appbootstrap.controller.payment;

import com.example.appbootstrap.controller.payment.dto.PaymentWebhookResponse;
import com.example.modulepayment.application.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Payments", description = "Payment webhook endpoints")
@RequestMapping("/api/payments/webhooks")
public class PaymentWebhooksController {

  private final PaymentWebhookService paymentWebhookService;

  public PaymentWebhooksController(PaymentWebhookService paymentWebhookService) {
    this.paymentWebhookService = paymentWebhookService;
  }

  @PostMapping(path = "/payos", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Receive PayOS webhook", description = "Verifies and processes PayOS payment webhooks")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Webhook received",
        content = @Content(schema = @Schema(implementation = PaymentWebhookResponse.class))),
    @ApiResponse(responseCode = "400", description = "Invalid webhook payload", content = @Content),
    @ApiResponse(responseCode = "401", description = "Invalid webhook signature", content = @Content),
    @ApiResponse(responseCode = "500", description = "Webhook processing failed", content = @Content)
  })
  PaymentWebhookResponse handlePayosWebhook(@RequestBody String rawPayload) {
    try {
      paymentWebhookService.handlePayosWebhook(rawPayload);
    } catch (DataIntegrityViolationException ignored) {
      // Concurrent duplicate webhook with same idempotency key: treat as acknowledged for PayOS.
    }
    return new PaymentWebhookResponse(true);
  }
}
