package com.example.modulepayment.controller;

import com.example.modulepayment.application.PaymentWebhookService;
import com.example.modulepayment.controller.dto.PaymentWebhookResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
  @Operation(
      summary = "Receive PayOS webhook",
      description = "Verifies and processes PayOS payment webhooks")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Webhook received",
        content = @Content(schema = @Schema(implementation = PaymentWebhookResponse.class))),
    @ApiResponse(responseCode = "400", description = "Invalid webhook payload", content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Invalid webhook signature",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Webhook processing failed",
        content = @Content)
  })
  PaymentWebhookResponse handlePayosWebhook(@RequestBody String rawPayload) {
    try {
      paymentWebhookService.handlePayosWebhook(rawPayload);
    } catch (DataIntegrityViolationException ignored) {
      // Concurrent duplicate webhook with same idempotency key: treat as acknowledged for PayOS.
    }
    return new PaymentWebhookResponse(true);
  }

  @PostMapping(path = "/stripe", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Receive Stripe webhook",
      description = "Verifies and processes Stripe Checkout payment webhooks")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Webhook received",
        content = @Content(schema = @Schema(implementation = PaymentWebhookResponse.class))),
    @ApiResponse(responseCode = "400", description = "Invalid webhook payload", content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Invalid webhook signature",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Webhook processing failed",
        content = @Content)
  })
  PaymentWebhookResponse handleStripeWebhook(
      @RequestBody String rawPayload,
      @RequestHeader(name = "Stripe-Signature", required = false) String signatureHeader) {
    try {
      paymentWebhookService.handleStripeWebhook(rawPayload, signatureHeader);
    } catch (DataIntegrityViolationException ignored) {
      // Concurrent duplicate webhook with same idempotency key: treat as acknowledged for Stripe.
    }
    return new PaymentWebhookResponse(true);
  }
}
