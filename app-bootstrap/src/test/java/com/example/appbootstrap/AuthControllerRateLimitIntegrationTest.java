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
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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
    properties = {
      "app.rate-limit.enabled=true",
      "app.rate-limit.fail-open=false",
      "app.rate-limit.login.window=PT10M",
      "app.rate-limit.login.email-limit=100",
      "app.rate-limit.login.client-limit=100",
      "app.rate-limit.login.pair-limit=1",
      "app.rate-limit.password-reset.window=PT15M",
      "app.rate-limit.password-reset.email-limit=100",
      "app.rate-limit.password-reset.client-limit=100",
      "app.rate-limit.password-reset.pair-limit=1",
      "app.rate-limit.password-reset-confirm.window=PT15M",
      "app.rate-limit.password-reset-confirm.client-limit=1",
      "app.rate-limit.refresh.window=PT5M",
      "app.rate-limit.refresh.user-limit=1",
      "app.rate-limit.refresh.client-limit=1"
    })
@AutoConfigureMockMvc
class AuthControllerRateLimitIntegrationTest {

  private static final String REFRESH_COOKIE_NAME = "refresh_token";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private RefreshTokenSessionRepository refreshTokenSessionRepository;
  @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private InMemoryRateLimitStore rateLimitStore;

  @BeforeEach
  void cleanState() {
    rateLimitStore.clear();
    passwordResetTokenRepository.deleteAll();
    refreshTokenSessionRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void shouldReturn429WhenLoginFailuresExceedThreshold() throws Exception {
    saveUser("login-limit@example.com", "secret123");

    mockMvc
        .perform(
            post("/api/auth/login")
                .header("X-Forwarded-For", "203.0.113.10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"login-limit@example.com",
                      "password":"wrong-password"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .header("X-Forwarded-For", "203.0.113.10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"login-limit@example.com",
                      "password":"wrong-password"
                    }
                    """))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
  }

  @Test
  void shouldUseClientIpInLoginRateLimitBuckets() throws Exception {
    saveUser("pair-limit@example.com", "secret123");

    mockMvc
        .perform(
            post("/api/auth/login")
                .header("X-Forwarded-For", "203.0.113.11")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"pair-limit@example.com",
                      "password":"wrong-password"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .header("X-Forwarded-For", "203.0.113.12")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"pair-limit@example.com",
                      "password":"wrong-password"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void shouldReturn429WhenPasswordResetRequestsExceedThreshold() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/password-reset/request")
                .header("X-Forwarded-For", "203.0.113.20")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset-limit@example.com\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/auth/password-reset/request")
                .header("X-Forwarded-For", "203.0.113.20")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"reset-limit@example.com\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("TOO_MANY_PASSWORD_RESET_REQUESTS"));
  }

  @Test
  void shouldReturn429WhenPasswordResetConfirmAttemptsExceedThreshold() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/password-reset/confirm")
                .header("X-Forwarded-For", "203.0.113.21")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "otp":"123456",
                      "newPassword":"newSecret123"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("RESET_OTP_INVALID"));

    mockMvc
        .perform(
            post("/api/auth/password-reset/confirm")
                .header("X-Forwarded-For", "203.0.113.21")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "otp":"654321",
                      "newPassword":"newSecret123"
                    }
                    """))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("TOO_MANY_PASSWORD_RESET_CONFIRM_ATTEMPTS"));
  }

  @Test
  void shouldReturn429WhenRefreshAttemptsExceedThreshold() throws Exception {
    saveUser("refresh-limit@example.com", "secret123");

    var loginResult =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .header("X-Forwarded-For", "203.0.113.30")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email":"refresh-limit@example.com",
                          "password":"secret123"
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn();

    String refreshToken = extractCookieValue(loginResult.getResponse(), REFRESH_COOKIE_NAME);

    var rotatedResult =
        mockMvc
            .perform(
                post("/api/auth/refresh")
                    .header("X-Forwarded-For", "203.0.113.30")
                    .cookie(refreshTokenCookie(refreshToken)))
            .andExpect(status().isOk())
            .andReturn();

    String newRefreshToken = extractCookieValue(rotatedResult.getResponse(), REFRESH_COOKIE_NAME);

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .header("X-Forwarded-For", "203.0.113.30")
                .cookie(refreshTokenCookie(newRefreshToken)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("TOO_MANY_REFRESH_ATTEMPTS"));
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

  private String extractCookieValue(
      org.springframework.mock.web.MockHttpServletResponse response, String cookieName) {
    for (String headerValue : response.getHeaders("Set-Cookie")) {
      if (headerValue.startsWith(cookieName + "=")) {
        int valueStart = cookieName.length() + 1;
        int valueEnd = headerValue.indexOf(';', valueStart);
        if (valueEnd < 0) {
          return headerValue.substring(valueStart);
        }
        return headerValue.substring(valueStart, valueEnd);
      }
    }
    throw new IllegalStateException("Cookie not found: " + cookieName);
  }

  private Cookie refreshTokenCookie(String token) {
    return new Cookie(REFRESH_COOKIE_NAME, token);
  }

  @TestConfiguration
  static class RateLimitTestConfig {

    @Bean
    @Primary
    InMemoryRateLimitStore inMemoryRateLimitStore() {
      return new InMemoryRateLimitStore();
    }
  }

  static class InMemoryRateLimitStore implements RateLimitStore {
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public long increment(String key, Duration window) {
      return counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public long getCount(String key) {
      return counters.getOrDefault(key, new AtomicLong()).get();
    }

    @Override
    public void delete(String key) {
      counters.remove(key);
    }

    void clear() {
      counters.clear();
    }
  }
}
