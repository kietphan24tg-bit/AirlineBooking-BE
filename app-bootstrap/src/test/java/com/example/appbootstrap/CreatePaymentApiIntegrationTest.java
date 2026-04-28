package com.example.appbootstrap;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.modulepayment.application.CreatePaymentResult;
import com.example.modulepayment.application.PaymentService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CreatePaymentApiIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PaymentService paymentService;

  @Test
  void shouldCreatePaymentWithoutAuthentication() throws Exception {
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

    mockMvc
        .perform(
            post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":10,\"method\":\"PAYOS\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.payment.id").value(123))
        .andExpect(jsonPath("$.data.payment.status").value("PENDING"))
        .andExpect(jsonPath("$.data.action.paymentUrl").value("https://pay.payos.vn/web/123"));
  }
}
