package com.example.appbootstrap;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.appbootstrap.security.JwtService;
import com.example.moduleidentity.entity.Role;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.repository.UserRepository;
import com.example.modulepayment.application.PaymentService;
import com.example.modulepayment.application.PaymentStatusView;
import com.example.modulepayment.exception.PaymentNotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PaymentStatusApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private PaymentService paymentService;

  @BeforeEach
  void cleanUsers() {
    userRepository.deleteAll();
  }

  @Test
  void shouldRejectPaymentStatusWithoutAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/payments/1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void shouldReturnPaymentStatusWhenAuthenticated() throws Exception {
    UserEntity user = userRepository.save(user("pay-status@example.com"));
    String token = jwtService.generateAccessToken(user.getEmail(), user.getId());
    when(paymentService.getPaymentForUser(1L, user.getId()))
        .thenReturn(
            new PaymentStatusView(
                1L,
                10L,
                new BigDecimal("1200000"),
                "VND",
                "PAYOS",
                "SUCCESS",
                OffsetDateTime.parse("2026-04-08T10:00:00Z"),
                OffsetDateTime.parse("2026-04-08T15:30:00Z"),
                "plink_1",
                "000201QR",
                null,
                null));

    mockMvc
        .perform(get("/api/payments/1").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.bookingId").value(10))
        .andExpect(jsonPath("$.data.status").value("SUCCESS"));
  }

  @Test
  void shouldReturn404WhenPaymentNotFoundForAuthenticatedUser() throws Exception {
    UserEntity user = userRepository.save(user("pay-missing@example.com"));
    String token = jwtService.generateAccessToken(user.getEmail(), user.getId());
    when(paymentService.getPaymentForUser(2L, user.getId()))
        .thenThrow(new PaymentNotFoundException(2L));

    mockMvc
        .perform(get("/api/payments/2").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_FOUND"));
  }

  private UserEntity user(String email) {
    OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    UserEntity user = new UserEntity();
    user.setEmail(email);
    user.setFirstName("Test");
    user.setLastName("User");
    user.setPasswordHash(passwordEncoder.encode("secret"));
    user.setRole(Role.USER);
    user.setAcceptTerms(true);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    return user;
  }
}
