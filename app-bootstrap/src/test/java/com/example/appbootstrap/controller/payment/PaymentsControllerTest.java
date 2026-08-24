package com.example.modulepayment.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.moduleidentity.entity.Role;
import com.example.modulepayment.application.ActivePayLaterSessionView;
import com.example.modulepayment.application.CreatePaymentResult;
import com.example.modulepayment.application.FlightChangePaymentService;
import com.example.modulepayment.application.PaymentMethodView;
import com.example.modulepayment.application.PaymentResultService;
import com.example.modulepayment.application.PaymentResultStatusView;
import com.example.modulepayment.application.PaymentResultSummaryView;
import com.example.modulepayment.application.PaymentService;
import com.example.modulepayment.application.PaymentStatusView;
import com.example.modulepayment.application.SeatChangePaymentService;
import com.example.modulepayment.application.ServiceChangePaymentService;
import com.example.modulepayment.controller.dto.CancelPaymentRequest;
import com.example.modulepayment.controller.dto.CreatePaymentRequest;
import com.example.modulepayment.controller.dto.CreatePaymentResponse;
import com.example.modulepayment.controller.dto.PaymentMethodsResponse;
import com.example.modulepayment.controller.dto.PaymentStatusResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentsControllerTest {

  @Mock private PaymentService paymentService;
  @Mock private PaymentResultService paymentResultService;
  @Mock private SeatChangePaymentService seatChangePaymentService;
  @Mock private ServiceChangePaymentService serviceChangePaymentService;
  @Mock private FlightChangePaymentService flightChangePaymentService;

  private PaymentsController controller;
  private Validator validator;

  @BeforeEach
  void setUp() {
    controller =
        new PaymentsController(
            paymentService,
            paymentResultService,
            seatChangePaymentService,
            serviceChangePaymentService,
            flightChangePaymentService);
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  void shouldMapPaymentMethodsResponse() {
    when(paymentService.getPaymentMethods(10L, 7L, false))
        .thenReturn(
            List.of(
                new PaymentMethodView(
                    "CARD",
                    "Cards & banking",
                    List.of(
                        new PaymentMethodView.PaymentMethodItemView(
                            "PAYOS_QR",
                            "Mobile Banking (VietQR)",
                            "PAYOS",
                            "http://localhost:8080/assets/payment-icons/vietqr.svg",
                            true,
                            null))),
                new PaymentMethodView(
                    "PAY_LATER",
                    "Pay later",
                    List.of(
                        new PaymentMethodView.PaymentMethodItemView(
                            "PAY_LATER",
                            "Hold booking, pay later",
                            "PAYOS",
                            "http://localhost:8080/assets/payment-icons/vietqr.svg",
                            true,
                            new PaymentMethodView.PaymentMethodMetaView(60, 0))))));
    when(paymentService.findActivePayLaterSession(10L, 7L))
        .thenReturn(
            Optional.of(
                new ActivePayLaterSessionView(
                    91L, OffsetDateTime.parse("2026-05-23T11:00:00+07:00"))));

    PaymentMethodsResponse response = controller.getPaymentMethods(principal(), 10L, false);

    assertEquals("CARD", response.groups().getFirst().type());
    assertEquals("PAYOS_QR", response.groups().getFirst().methods().getFirst().code());
    assertEquals("PAYOS", response.groups().getFirst().methods().getFirst().provider());
    assertEquals(
        "http://localhost:8080/assets/payment-icons/vietqr.svg",
        response.groups().getFirst().methods().getFirst().icon());
    assertEquals(true, response.groups().getFirst().methods().getFirst().supported());
    assertEquals(60, response.groups().get(1).methods().getFirst().meta().holdDurationMinutes());
    assertEquals(91L, response.activePayLater().paymentId());
  }

  @Test
  void shouldAllowPublicPaymentMethodsWhenNoPrincipal() {
    when(paymentService.getPaymentMethodsPublic(10L, true))
        .thenReturn(
            List.of(
                new PaymentMethodView(
                    "CARD",
                    "Cards & banking",
                    List.of(
                        new PaymentMethodView.PaymentMethodItemView(
                            "PAYOS_QR",
                            "Mobile Banking (VietQR)",
                            "PAYOS",
                            "vietqr.png",
                            true,
                            null)))));
    when(paymentService.findActivePayLaterSessionPublic(10L)).thenReturn(Optional.empty());

    PaymentMethodsResponse response = controller.getPaymentMethods(null, 10L, true);

    assertEquals(1, response.groups().size());
    assertEquals("PAYOS_QR", response.groups().getFirst().methods().getFirst().code());
  }

  @Test
  void shouldPassOnlySupportedFlagToService() {
    when(paymentService.getPaymentMethods(10L, 7L, true))
        .thenReturn(
            List.of(
                new PaymentMethodView(
                    "CARD",
                    "Cards & banking",
                    List.of(
                        new PaymentMethodView.PaymentMethodItemView(
                            "PAYOS_QR",
                            "Mobile Banking (VietQR)",
                            "PAYOS",
                            "http://localhost:8080/assets/payment-icons/vietqr.svg",
                            true,
                            null)))));
    when(paymentService.findActivePayLaterSession(10L, 7L)).thenReturn(Optional.empty());

    PaymentMethodsResponse response = controller.getPaymentMethods(principal(), 10L, true);

    assertEquals(1, response.groups().size());
    assertEquals("PAYOS_QR", response.groups().getFirst().methods().getFirst().code());
  }

  @Test
  void shouldMapCreatePaymentResponse() {
    when(paymentService.createPayment(argThat(command -> command.userId().equals(7L))))
        .thenReturn(
            new CreatePaymentResult(
                new CreatePaymentResult.PaymentView(
                    123L,
                    10L,
                    new BigDecimal("1200000"),
                    "VND",
                    "PAYOS",
                    "PENDING",
                    OffsetDateTime.parse("2026-04-08T15:30:00Z"),
                    "plink_123",
                    "000201PAYOSQR"),
                new CreatePaymentResult.PaymentActionView(
                    "QR", "000201PAYOSQR", null, "https://pay.payos.vn/web/123")));

    CreatePaymentResponse response =
        controller.createPayment(principal(), new CreatePaymentRequest(10L, "PAYOS"));

    assertEquals(123L, response.payment().id());
    assertEquals("PENDING", response.payment().status());
    assertEquals("QR", response.action().type());
    assertEquals("https://pay.payos.vn/web/123", response.action().paymentUrl());
  }

  @Test
  void shouldAllowPublicCreatePaymentWhenNoPrincipal() {
    when(paymentService.createPaymentPublic(
            argThat(command -> command.bookingId().equals(10L) && command.userId() == null)))
        .thenReturn(
            new CreatePaymentResult(
                new CreatePaymentResult.PaymentView(
                    123L,
                    10L,
                    new BigDecimal("1200000"),
                    "VND",
                    "PAYOS",
                    "PENDING",
                    OffsetDateTime.parse("2026-04-08T15:30:00Z"),
                    "plink_123",
                    "000201PAYOSQR"),
                new CreatePaymentResult.PaymentActionView(
                    "QR", "000201PAYOSQR", null, "https://pay.payos.vn/web/123")));

    CreatePaymentResponse response =
        controller.createPayment(null, new CreatePaymentRequest(10L, "PAYOS"));

    assertEquals(123L, response.payment().id());
    assertEquals("https://pay.payos.vn/web/123", response.action().paymentUrl());
  }

  @Test
  void shouldMapPaymentStatusResponse() {
    when(paymentService.getPaymentForUser(5L, 7L))
        .thenReturn(
            new PaymentStatusView(
                5L,
                10L,
                new BigDecimal("1200000"),
                "VND",
                "PAYOS",
                "SUCCESS",
                OffsetDateTime.parse("2026-04-08T10:00:00Z"),
                OffsetDateTime.parse("2026-04-08T15:30:00Z"),
                "plink_5",
                "000201QR",
                null,
                null));

    PaymentStatusResponse response = controller.getPaymentStatus(principal(), 5L);

    assertEquals(5L, response.id());
    assertEquals(10L, response.bookingId());
    assertEquals("SUCCESS", response.status());
    assertEquals("000201QR", response.qrData());
    assertEquals("plink_5", response.transactionId());
  }

  @Test
  void shouldMapCancelPaymentResponse() {
    when(paymentService.cancelPaymentForUser(5L, 7L, "Changed my mind"))
        .thenReturn(
            new PaymentStatusView(
                5L,
                10L,
                new BigDecimal("1200000"),
                "VND",
                "PAYOS",
                "CANCELLED",
                null,
                OffsetDateTime.parse("2026-04-08T15:30:00Z"),
                "plink_5",
                "000201QR",
                null,
                "https://pay.payos.vn/web/5"));

    PaymentStatusResponse response =
        controller.cancelPayment(principal(), 5L, new CancelPaymentRequest("Changed my mind"));

    assertEquals(5L, response.id());
    assertEquals("CANCELLED", response.status());
    assertEquals("plink_5", response.transactionId());
  }

  @Test
  void shouldAllowPublicCancelWhenNoPrincipal() {
    when(paymentService.cancelPaymentPublic(5L, "Changed my mind"))
        .thenReturn(
            new PaymentStatusView(
                5L,
                10L,
                new BigDecimal("1200000"),
                "VND",
                "PAYOS",
                "CANCELLED",
                null,
                OffsetDateTime.parse("2026-04-08T15:30:00Z"),
                "plink_5",
                "000201QR",
                null,
                "https://pay.payos.vn/web/5"));

    PaymentStatusResponse response =
        controller.cancelPayment(null, 5L, new CancelPaymentRequest("Changed my mind"));

    assertEquals("CANCELLED", response.status());
  }

  @Test
  void shouldMapPaymentResultStatusResponse() {
    when(paymentResultService.getResultStatusForUser(5L, 7L))
        .thenReturn(
            new PaymentResultStatusView(
                5L,
                10L,
                "SUCCESS",
                "CONFIRMED",
                "SUCCESS",
                OffsetDateTime.parse("2026-04-08T15:30:00Z"),
                new BigDecimal("1200000"),
                "VND",
                "PAY_LATER",
                "plink_5",
                "000201QR",
                "https://img.qr/5.png",
                "https://pay.payos.vn/web/5",
                "ABC123",
                new PaymentResultStatusView.PollingView(false, null),
                null));

    PaymentResultStatusView response = controller.getPaymentResultStatus(principal(), 5L);

    assertEquals(5L, response.paymentId());
    assertEquals(10L, response.bookingId());
    assertEquals("SUCCESS", response.resultPage());
    assertEquals("ABC123", response.pnrCode());
    assertFalse(response.polling().shouldPoll());
  }

  @Test
  void shouldMapPaymentResultSummaryResponse() {
    when(paymentResultService.getResultSummaryForUser(5L, 7L))
        .thenReturn(
            new PaymentResultSummaryView(
                5L,
                10L,
                "SUCCESS",
                "CONFIRMED",
                new PaymentResultSummaryView.BookingView(
                    "PNR123",
                    OffsetDateTime.parse("2026-04-08T09:00:00Z"),
                    OffsetDateTime.parse("2026-04-08T10:00:00Z"),
                    OffsetDateTime.parse("2026-04-08T15:30:00Z"),
                    "VND",
                    new BigDecimal("1200000")),
                new PaymentResultSummaryView.ContactView("Test User", "user@example.com", "+8496"),
                new PaymentResultSummaryView.PaymentView(
                    "PAYOS", "plink_5", new BigDecimal("1200000"), "VND"),
                new PaymentResultSummaryView.SummaryView(1, 1, new BigDecimal("1200000"), "VND"),
                List.of(),
                List.of(),
                List.of(),
                null));

    PaymentResultSummaryView response = controller.getPaymentResultSummary(principal(), 5L);

    assertEquals(5L, response.paymentId());
    assertEquals("PNR123", response.booking().pnrCode());
    assertEquals("PAYOS", response.payment().method());
  }

  @Test
  void shouldValidateCreatePaymentRequest() {
    var violations = validator.validate(new CreatePaymentRequest(null, " "));

    assertEquals(2, violations.size());
  }

  private static AppUserPrincipal principal() {
    return new AppUserPrincipal(7L, "user@example.com", "secret", Role.USER, null);
  }
}
