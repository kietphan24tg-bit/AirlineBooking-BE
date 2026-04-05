package com.example.appbootstrap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.moduleidentity.entity.Role;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.port.RateLimitStore;
import com.example.moduleidentity.repository.PasswordResetTokenRepository;
import com.example.moduleidentity.repository.RefreshTokenSessionRepository;
import com.example.moduleidentity.repository.UserRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.rate-limit.enabled=true", "app.rate-limit.fail-open=true"})
@AutoConfigureMockMvc
class AuthControllerRateLimitFailOpenIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private RefreshTokenSessionRepository refreshTokenSessionRepository;
  @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void cleanState() {
    passwordResetTokenRepository.deleteAll();
    refreshTokenSessionRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void shouldFailOpenForLoginWhenRateLimitBackendBreaks() throws Exception {
    saveUser("fail-open@example.com", "secret123");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"fail-open@example.com",
                      "password":"wrong-password"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void shouldFailOpenForPasswordResetRequestWhenRateLimitBackendBreaks() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"fail-open-reset@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.message").value("If the email exists, a password reset link will be sent"));
  }

  private UserEntity saveUser(String email, String password) {
    UserEntity user = new UserEntity();
    OffsetDateTime now = OffsetDateTime.now();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setFirstName("Jane");
    user.setLastName("Doe");
    user.setRole(Role.USER);
    user.setAcceptTerms(true);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    return userRepository.save(user);
  }

  @TestConfiguration
  static class BrokenRateLimitStoreConfig {

    @Bean
    @Primary
    RateLimitStore brokenRateLimitStore() {
      return new RateLimitStore() {
        @Override
        public long increment(String key, Duration window) {
          throw new IllegalStateException("rate-limit-store-down");
        }

        @Override
        public long getCount(String key) {
          throw new IllegalStateException("rate-limit-store-down");
        }

        @Override
        public void delete(String key) {
          throw new IllegalStateException("rate-limit-store-down");
        }
      };
    }
  }
}
