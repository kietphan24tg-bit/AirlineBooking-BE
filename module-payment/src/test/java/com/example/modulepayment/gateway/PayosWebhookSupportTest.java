package com.example.modulepayment.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.common.exception.AppException;
import com.example.modulepayment.config.PaymentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PayosWebhookSupportTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldParseAndVerifySignedPayosWebhook() throws Exception {
    JsonNode dataNode =
        objectMapper.readTree(
            """
            {
              "amount": 1200000,
              "code": "00",
              "currency": "VND",
              "description": "AB0000123",
              "orderCode": 123,
              "paymentLinkId": "plink_123",
              "reference": "FT25223370259934",
              "transactionDateTime": "2025-08-10 10:03:09"
            }
            """);
    String signaturePayload = PayosWebhookSupport.buildWebhookSignaturePayload(dataNode);
    String signature = PayosPaymentGateway.sign(signaturePayload, "checksum-key");

    PayosWebhookSupport support =
        new PayosWebhookSupport(
            objectMapper,
            new PaymentProperties(
                "payos",
                "webhook-secret",
                "http://localhost:3000/payment/success",
                "http://localhost:3000/payment/cancel",
                "http://localhost:8080",
                new PaymentProperties.Payos(
                    "client-id", "api-key", "checksum-key", "https://api-merchant.payos.vn")));

    PayosWebhookSupport.NormalizedPayosWebhookEvent result =
        support.parseAndVerify(
            """
            {
              "code": "00",
              "desc": "success",
              "success": true,
              "data": {
                "amount": 1200000,
                "code": "00",
                "currency": "VND",
                "description": "AB0000123",
                "orderCode": 123,
                "paymentLinkId": "plink_123",
                "reference": "FT25223370259934",
                "transactionDateTime": "2025-08-10 10:03:09"
              },
              "signature": "%s"
            }
            """
                .formatted(signature));

    assertEquals("PAYOS", result.provider());
    assertEquals("PAYMENT_SUCCEEDED", result.eventType());
    assertEquals(123L, result.orderCode());
    assertEquals("plink_123", result.paymentLinkId());
    assertEquals("PAYOS_123_plink_123_PAYMENT_SUCCEEDED", result.idempotencyKey());
  }

  @Test
  void shouldRejectInvalidSignature() {
    PayosWebhookSupport support =
        new PayosWebhookSupport(
            objectMapper,
            new PaymentProperties(
                "payos",
                "webhook-secret",
                "http://localhost:3000/payment/success",
                "http://localhost:3000/payment/cancel",
                "http://localhost:8080",
                new PaymentProperties.Payos(
                    "client-id", "api-key", "checksum-key", "https://api-merchant.payos.vn")));

    AppException exception =
        assertThrows(
            AppException.class,
            () ->
                support.parseAndVerify(
                    """
                    {
                      "code": "00",
                      "desc": "success",
                      "success": true,
                      "data": {
                        "amount": 1200000,
                        "code": "00",
                        "currency": "VND",
                        "description": "AB0000123",
                        "orderCode": 123,
                        "paymentLinkId": "plink_123",
                        "reference": "FT25223370259934",
                        "transactionDateTime": "2025-08-10 10:03:09"
                      },
                      "signature": "invalid"
                    }
                    """));

    assertEquals("PAYMENT_WEBHOOK_INVALID_SIGNATURE", exception.getCode());
  }
}
