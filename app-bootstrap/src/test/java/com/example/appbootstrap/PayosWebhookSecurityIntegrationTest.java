package com.example.appbootstrap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PayosWebhookSecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldExposePayosWebhookWithoutAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/api/payments/webhooks/payos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
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
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("PAYMENT_WEBHOOK_INVALID_SIGNATURE"));
  }
}
