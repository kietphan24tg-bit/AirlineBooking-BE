package com.example.appbootstrap.controller.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.controller.payment.dto.CreatePaymentRequest;
import com.example.appbootstrap.controller.payment.dto.CreatePaymentResponse;
import com.example.appbootstrap.controller.payment.dto.PaymentMethodsResponse;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.moduleidentity.entity.Role;
import com.example.modulepayment.application.CreatePaymentResult;
import com.example.modulepayment.application.PaymentMethodView;
import com.example.modulepayment.application.PaymentService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentsControllerTest {

  @Mock private PaymentService paymentService;

  private PaymentsController controller;
  private Validator validator;

  @BeforeEach
  void setUp() {
    controller = new PaymentsController(paymentService);
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  void shouldMapPaymentMethodsResponse() {
    when(paymentService.getPaymentMethods(10L, 7L, false))
        .thenReturn(
            List.of(
                new PaymentMethodView(
                    "CARD",
                    "Thẻ & ngân hàng",
                    List.of(
                        new PaymentMethodView.PaymentMethodItemView(
                            "PAYOS_QR",
                            "Mobile Banking (VietQR)",
                            "PAYOS",
                            "vietqr.png",
                            true,
                            null))),
                new PaymentMethodView(
                    "PAY_LATER",
                    "Thanh toán sau",
                    List.of(
                        new PaymentMethodView.PaymentMethodItemView(
                            "PAY_LATER",
                            "Giữ chỗ, thanh toán sau",
                            "INTERNAL",
                            null,
                            false,
                            new PaymentMethodView.PaymentMethodMetaView(30, 0))))));

    PaymentMethodsResponse response = controller.getPaymentMethods(principal(), 10L, false);

    assertEquals("CARD", response.groups().getFirst().type());
    assertEquals("PAYOS_QR", response.groups().getFirst().methods().getFirst().code());
    assertEquals("PAYOS", response.groups().getFirst().methods().getFirst().provider());
    assertEquals("vietqr.png", response.groups().getFirst().methods().getFirst().icon());
    assertEquals(true, response.groups().getFirst().methods().getFirst().supported());
    assertEquals(30, response.groups().get(1).methods().getFirst().meta().holdDurationMinutes());
  }

  @Test
  void shouldPassOnlySupportedFlagToService() {
    when(paymentService.getPaymentMethods(10L, 7L, true))
        .thenReturn(
            List.of(
                new PaymentMethodView(
                    "CARD",
                    "Thẻ & ngân hàng",
                    List.of(
                        new PaymentMethodView.PaymentMethodItemView(
                            "PAYOS_QR",
                            "Mobile Banking (VietQR)",
                            "PAYOS",
                            "vietqr.png",
                            true,
                            null)))));

    PaymentMethodsResponse response = controller.getPaymentMethods(principal(), 10L, true);

    assertEquals(1, response.groups().size());
    assertEquals("PAYOS_QR", response.groups().getFirst().methods().getFirst().code());
  }

  @Test
  void shouldMapCreatePaymentResponse() {
    when(paymentService.createPayment(any()))
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
  void shouldValidateCreatePaymentRequest() {
    var violations = validator.validate(new CreatePaymentRequest(null, " "));

    assertEquals(2, violations.size());
  }

  private static AppUserPrincipal principal() {
    return new AppUserPrincipal(7L, "user@example.com", "secret", Role.USER, null);
  }
}
