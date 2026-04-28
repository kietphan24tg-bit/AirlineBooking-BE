package com.example.modulepayment.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.exception.AppException;
import com.example.modulepayment.config.PaymentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PayosPaymentGatewayTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void shouldGenerateStableSignaturePayload() {
    String payload =
        PayosPaymentGateway.buildSignaturePayload(
            1200000L,
            123L,
            "AB0000123",
            "http://localhost:3000/payment/success?paymentId=123",
            "http://localhost:3000/payment/cancel?paymentId=123");

    assertEquals(
        "amount=1200000&cancelUrl=http://localhost:3000/payment/cancel?paymentId=123&description=AB0000123&orderCode=123&returnUrl=http://localhost:3000/payment/success?paymentId=123",
        payload);
    assertEquals(
        "8a37b8f8400b9b04eb17aa2c979afe0cb726accb27981322e17f9e308e35475a",
        PayosPaymentGateway.sign(payload, "checksum-key"));
  }

  @Test
  void shouldMapPayosResponseToGatewayResult() throws Exception {
    AtomicReference<String> bodyRef = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v2/payment-requests",
        exchange -> {
          bodyRef.set(readBody(exchange));
          respond(
              exchange,
              200,
              """
              {
                "code": "00",
                "desc": "success",
                "data": {
                  "paymentLinkId": "plink_123",
                  "checkoutUrl": "https://pay.payos.vn/web/123",
                  "qrCode": "000201PAYOSQR",
                  "status": "PENDING"
                }
              }
              """);
        });
    server.start();

    PayosPaymentGateway gateway =
        new PayosPaymentGateway(
            new PaymentProperties(
                "payos",
                "webhook-secret",
                "http://localhost:5173/payment/success",
                "http://localhost:5173/payment/cancel",
                "https://airline-booking-staging.onrender.com/",
                new PaymentProperties.Payos(
                    "client-id",
                    "api-key",
                    "checksum-key",
                    "http://localhost:" + server.getAddress().getPort())),
            new ObjectMapper());

    PaymentGateway.PaymentGatewayResult result =
        gateway.createPaymentLink(
            new PaymentGateway.PaymentGatewayRequest(
                123L,
                123L,
                new BigDecimal("1200000"),
                "VND",
                "AB0000123",
                "http://localhost:3000/payment/success?paymentId=123",
                "http://localhost:3000/payment/cancel?paymentId=123",
                "ABC123",
                "user@example.com",
                "0901234567",
                OffsetDateTime.parse("2026-04-08T15:30:00Z")));

    assertEquals("plink_123", result.paymentLinkId());
    assertEquals("https://pay.payos.vn/web/123", result.checkoutUrl());
    assertEquals("000201PAYOSQR", result.qrCode());
    assertEquals("QR", result.actionType());
    assertTrue(bodyRef.get().contains("\"orderCode\":123"));
    assertTrue(bodyRef.get().contains("\"signature\":\""));
  }

  @Test
  void shouldRejectHttp200WithNonSuccessCode() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v2/payment-requests",
        exchange ->
            respond(
                exchange,
                200,
                """
                {
                  "code": "231",
                  "desc": "Invalid account",
                  "data": null
                }
                """));
    server.start();

    PayosPaymentGateway gateway =
        new PayosPaymentGateway(
            new PaymentProperties(
                "payos",
                "webhook-secret",
                "http://localhost:3000/payment/success",
                "http://localhost:3000/payment/cancel",
                "http://localhost:8080",
                new PaymentProperties.Payos(
                    "client-id",
                    "api-key",
                    "checksum-key",
                    "http://localhost:" + server.getAddress().getPort())),
            new ObjectMapper());

    AppException ex =
        assertThrows(
            AppException.class,
            () ->
                gateway.createPaymentLink(
                    new PaymentGateway.PaymentGatewayRequest(
                        123L,
                        123L,
                        new BigDecimal("1200000"),
                        "VND",
                        "AB0000123",
                        "http://localhost:3000/payment/success?paymentId=123",
                        "http://localhost:3000/payment/cancel?paymentId=123",
                        "ABC123",
                        "user@example.com",
                        "0901234567",
                        OffsetDateTime.parse("2026-04-08T15:30:00Z"))));

    assertEquals("PAYMENT_PROVIDER_ERROR", ex.getCode());
  }

  @Test
  void shouldCallCancelPaymentEndpointWithReason() throws Exception {
    AtomicReference<String> bodyRef = new AtomicReference<>();
    AtomicReference<String> pathRef = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v2/payment-requests/123/cancel",
        exchange -> {
          pathRef.set(exchange.getRequestURI().getPath());
          bodyRef.set(readBody(exchange));
          respond(
              exchange,
              200,
              """
              {
                "code": "00",
                "desc": "success",
                "data": {
                  "status": "CANCELLED"
                }
              }
              """);
        });
    server.start();

    PayosPaymentGateway gateway =
        new PayosPaymentGateway(
            new PaymentProperties(
                "payos",
                "webhook-secret",
                "http://localhost:3000/payment/success",
                "http://localhost:3000/payment/cancel",
                "http://localhost:8080",
                new PaymentProperties.Payos(
                    "client-id",
                    "api-key",
                    "checksum-key",
                    "http://localhost:" + server.getAddress().getPort())),
            new ObjectMapper());

    gateway.cancelPaymentLink(
        new PaymentGateway.CancelPaymentLinkRequest(123L, 123L, "Changed my mind"));

    assertEquals("/v2/payment-requests/123/cancel", pathRef.get());
    assertTrue(bodyRef.get().contains("cancellationReason"));
    assertTrue(bodyRef.get().contains("Changed my mind"));
  }

  private static String readBody(HttpExchange exchange) throws IOException {
    return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] content = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, content.length);
    try (OutputStream outputStream = exchange.getResponseBody()) {
      outputStream.write(content);
    }
  }
}
