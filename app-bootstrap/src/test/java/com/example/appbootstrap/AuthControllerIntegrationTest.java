package com.example.appbootstrap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.appbootstrap.security.JwtService;
import com.example.moduleidentity.domain.security.TokenHashingService;
import com.example.moduleidentity.entity.PasswordResetTokenEntity;
import com.example.moduleidentity.entity.RefreshTokenSessionEntity;
import com.example.moduleidentity.entity.Role;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.repository.PasswordResetTokenRepository;
import com.example.moduleidentity.repository.RefreshTokenSessionRepository;
import com.example.moduleidentity.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

  private static final String REFRESH_COOKIE_NAME = "refresh_token";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private RefreshTokenSessionRepository refreshTokenSessionRepository;
  @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private TokenHashingService tokenHashingService;
  @Autowired private JwtService jwtService;

  @BeforeEach
  void cleanState() {
    passwordResetTokenRepository.deleteAll();
    refreshTokenSessionRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void shouldRegisterUserAndReturnSummary() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"user@example.com",
                      "password":"secret123",
                      "firstName":"Jane",
                      "lastName":"Doe",
                      "acceptTerms":true
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.user.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.user.firstName").value("Jane"))
        .andExpect(jsonPath("$.data.user.role").value("USER"))
        .andExpect(jsonPath("$.message").value("Registration successful"));
  }

  @Test
  void shouldRejectDuplicateEmailOnRegister() throws Exception {
    saveUser("user@example.com", "secret123");

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"user@example.com",
                      "password":"secret123",
                      "firstName":"Jane",
                      "lastName":"Doe",
                      "acceptTerms":true
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
  }

  @Test
  void shouldLoginRefreshAndLogoutSuccessfully() throws Exception {
    saveUser("user@example.com", "secret123");

    var loginResult =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email":"user@example.com",
                          "password":"secret123"
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
            .andExpect(jsonPath("$.message").value("Operation successful"))
            .andReturn();
    String refreshToken = extractCookieValue(loginResult.getResponse(), REFRESH_COOKIE_NAME);

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(refreshToken)))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.message").value("Operation successful"));

    mockMvc
        .perform(post("/api/auth/logout").cookie(refreshTokenCookie(refreshToken)))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(
            header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(refreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REVOKED"));
  }

  @Test
  void shouldRevokeOldRefreshSessionAndCreateNewSessionOnRefresh() throws Exception {
    UserEntity user = saveUser("rotate@example.com", "secret123");

    var loginResult =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email":"rotate@example.com",
                          "password":"secret123"
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn();

    String oldRefreshToken = extractCookieValue(loginResult.getResponse(), REFRESH_COOKIE_NAME);
    String oldRefreshTokenHash = tokenHashingService.hashToken(oldRefreshToken);

    var refreshResult =
        mockMvc
            .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(oldRefreshToken)))
            .andExpect(status().isOk())
            .andReturn();

    String newRefreshToken = extractCookieValue(refreshResult.getResponse(), REFRESH_COOKIE_NAME);
    String newRefreshTokenHash = tokenHashingService.hashToken(newRefreshToken);

    var sessions = refreshTokenSessionRepository.findAll();
    org.assertj.core.api.Assertions.assertThat(sessions).hasSize(2);
    org.assertj.core.api.Assertions.assertThat(sessions)
        .anySatisfy(
            session -> {
              if (session.getRefreshTokenHash().equals(oldRefreshTokenHash)) {
                org.assertj.core.api.Assertions.assertThat(session.isRevoked()).isTrue();
              }
            });
    org.assertj.core.api.Assertions.assertThat(sessions)
        .anySatisfy(
            session -> {
              if (session.getRefreshTokenHash().equals(newRefreshTokenHash)) {
                org.assertj.core.api.Assertions.assertThat(session.isRevoked()).isFalse();
                org.assertj.core.api.Assertions.assertThat(session.getUser().getId())
                    .isEqualTo(user.getId());
              }
            });
  }

  @Test
  void shouldAllowIdempotentLogout() throws Exception {
    saveUser("logout@example.com", "secret123");

    var loginResult =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email":"logout@example.com",
                          "password":"secret123"
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn();

    String refreshToken = extractCookieValue(loginResult.getResponse(), REFRESH_COOKIE_NAME);

    mockMvc
        .perform(post("/api/auth/logout").cookie(refreshTokenCookie(refreshToken)))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/api/auth/logout").cookie(refreshTokenCookie(refreshToken)))
        .andExpect(status().isOk());
  }

  @Test
  void shouldReturnInvalidCredentialsWhenLoginFails() throws Exception {
    saveUser("user@example.com", "secret123");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"user@example.com",
                      "password":"wrong-password"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.error.message").value("Email or password is incorrect"))
        .andExpect(jsonPath("$.statusCode").value(401))
        .andExpect(jsonPath("$.path").value("/api/auth/login"))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }

  @Test
  void shouldReturnGenericSuccessForPasswordResetRequestEvenWhenUserMissing() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(
            jsonPath("$.message").value("If the email exists, a password reset link will be sent"));
  }

  @Test
  void shouldResetPasswordWithValidTokenAndRejectInvalidToken() throws Exception {
    UserEntity user = saveUser("user@example.com", "secret123");

    PasswordResetTokenEntity token = new PasswordResetTokenEntity();
    token.setUser(user);
    token.setTokenHash(tokenHashingService.hashToken("reset-token"));
    token.setCreatedAt(OffsetDateTime.now());
    token.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
    token.setUsed(false);
    passwordResetTokenRepository.save(token);

    mockMvc
        .perform(
            post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token":"reset-token",
                      "newPassword":"newSecret123"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.message").value("Password reset successful"));

    mockMvc
        .perform(
            post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token":"unknown-token",
                      "newPassword":"newSecret123"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("RESET_TOKEN_INVALID"));
  }

  @Test
  void shouldRejectUsedPasswordResetToken() throws Exception {
    UserEntity user = saveUser("used-token@example.com", "secret123");

    PasswordResetTokenEntity token = passwordResetToken(user, "used-reset-token");
    token.setUsed(true);
    token.setUsedAt(OffsetDateTime.now());
    passwordResetTokenRepository.save(token);

    mockMvc
        .perform(
            post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token":"used-reset-token",
                      "newPassword":"newSecret123"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("RESET_TOKEN_INVALID"));
  }

  @Test
  void shouldRejectExpiredPasswordResetToken() throws Exception {
    UserEntity user = saveUser("expired-token@example.com", "secret123");

    PasswordResetTokenEntity token = passwordResetToken(user, "expired-reset-token");
    token.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
    passwordResetTokenRepository.save(token);

    mockMvc
        .perform(
            post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token":"expired-reset-token",
                      "newPassword":"newSecret123"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("RESET_TOKEN_INVALID"));
  }

  @Test
  void shouldRevokeAllActiveSessionsWhenPasswordResetSucceeds() throws Exception {
    UserEntity user = saveUser("reset-revoke@example.com", "secret123");
    createRefreshSession(
        user, jwtService.generateRefreshToken(user.getEmail(), user.getId()), false);
    createRefreshSession(
        user, jwtService.generateRefreshToken(user.getEmail(), user.getId()), false);

    PasswordResetTokenEntity token = passwordResetToken(user, "reset-revoke-token");
    passwordResetTokenRepository.save(token);

    mockMvc
        .perform(
            post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "token":"reset-revoke-token",
                      "newPassword":"newSecret123"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Password reset successful"));

    org.assertj.core.api.Assertions.assertThat(
            refreshTokenSessionRepository.findAllByUser_IdAndRevokedFalse(user.getId()))
        .isEmpty();
    UserEntity updatedUser = userRepository.findById(user.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updatedUser.getTokenInvalidBefore()).isNotNull();
  }

  @Test
  void shouldReturnValidationErrorDetailsForInvalidDto() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"bad-email",
                      "password":"123",
                      "firstName":"",
                      "lastName":"",
                      "acceptTerms":true
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.details.email").isNotEmpty())
        .andExpect(jsonPath("$.error.details.password").isNotEmpty())
        .andExpect(jsonPath("$.error.details.firstName").isNotEmpty())
        .andExpect(jsonPath("$.error.details.lastName").isNotEmpty());
  }

  @Test
  void shouldValidateBlankTokensAndEmails() throws Exception {
    mockMvc
        .perform(post("/api/auth/refresh"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REQUIRED"));

    mockMvc
        .perform(
            post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.details.email").isNotEmpty());

    mockMvc
        .perform(
            post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\",\"newPassword\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.details.token").isNotEmpty())
        .andExpect(jsonPath("$.error.details.newPassword").isNotEmpty());
  }

  @Test
  void shouldRejectRegisterWhenTermsOrPhoneCountryRulesAreInvalid() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"terms@example.com",
                      "password":"secret123",
                      "firstName":"Jane",
                      "lastName":"Doe",
                      "acceptTerms":false
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("TERMS_NOT_ACCEPTED"))
        .andExpect(jsonPath("$.path").value("/api/auth/register"))
        .andExpect(jsonPath("$.requestId").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isNotEmpty());

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"phone-only@example.com",
                      "password":"secret123",
                      "firstName":"Jane",
                      "lastName":"Doe",
                      "acceptTerms":true,
                      "phone":"+84901234567"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PHONE_COUNTRY_REQUIRED"));

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"country-only@example.com",
                      "password":"secret123",
                      "firstName":"Jane",
                      "lastName":"Doe",
                      "acceptTerms":true,
                      "nationality":"VN"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PHONE_COUNTRY_REQUIRED"));
  }

  @Test
  void shouldRejectRegisterWhenCountryOrPhoneFormatIsInvalid() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"bad-country@example.com",
                      "password":"secret123",
                      "firstName":"Jane",
                      "lastName":"Doe",
                      "acceptTerms":true,
                      "phone":"+84901234567",
                      "nationality":"VNM"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_COUNTRY_CODE"));

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"bad-phone@example.com",
                      "password":"secret123",
                      "firstName":"Jane",
                      "lastName":"Doe",
                      "acceptTerms":true,
                      "phone":"123",
                      "nationality":"VN"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_PHONE_NUMBER"));
  }

  @Test
  void shouldRejectExpiredRefreshTokenSession() throws Exception {
    UserEntity user = saveUser("expired-refresh@example.com", "secret123");
    String expiredRefreshToken =
        customToken(
            user.getEmail(),
            user.getId(),
            "refresh",
            Instant.now().minusSeconds(120),
            Instant.now().minusSeconds(60));
    createRefreshSession(user, expiredRefreshToken, OffsetDateTime.now().minusMinutes(1), false);

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(expiredRefreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_EXPIRED"));
  }

  @Test
  void shouldRejectRevokedRefreshTokenSession() throws Exception {
    UserEntity user = saveUser("revoked-refresh@example.com", "secret123");
    String refreshToken = jwtService.generateRefreshToken(user.getEmail(), user.getId());
    createRefreshSession(user, refreshToken, OffsetDateTime.now().plusDays(1), true);

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(refreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REVOKED"));
  }

  @Test
  void shouldRejectAccessTokenUsedAsRefreshToken() throws Exception {
    UserEntity user = saveUser("wrong-type@example.com", "secret123");
    String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getId());
    createRefreshSession(user, accessToken, OffsetDateTime.now().plusMinutes(15), false);

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(accessToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));
  }

  @Test
  void shouldRejectRefreshTokenWithMismatchedUserId() throws Exception {
    UserEntity user = saveUser("mismatch@example.com", "secret123");
    String refreshToken =
        customToken(
            user.getEmail(),
            user.getId() + 99,
            "refresh",
            Instant.now(),
            Instant.now().plusSeconds(3600));
    createRefreshSession(user, refreshToken, OffsetDateTime.now().plusDays(1), false);

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(refreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));
  }

  @Test
  void shouldRejectRefreshTokenIssuedBeforeTokenInvalidBefore() throws Exception {
    UserEntity user = saveUser("invalid-before@example.com", "secret123");
    String refreshToken = jwtService.generateRefreshToken(user.getEmail(), user.getId());
    createRefreshSession(user, refreshToken, OffsetDateTime.now().plusDays(1), false);

    try {
      Thread.sleep(25L);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
    user.setTokenInvalidBefore(OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC));
    user.setUpdatedAt(OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC));
    userRepository.save(user);

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(refreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));
  }

  @Test
  void shouldRejectMalformedRefreshTokenWhenSessionExists() throws Exception {
    UserEntity user = saveUser("malformed@example.com", "secret123");
    String malformedRefreshToken = "not-a-jwt-token";
    createRefreshSession(user, malformedRefreshToken, OffsetDateTime.now().plusDays(1), false);

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(malformedRefreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));
  }

  @Test
  void shouldRejectRefreshTokenWithInvalidSignature() throws Exception {
    UserEntity user = saveUser("bad-signature@example.com", "secret123");
    String wrongSignatureToken =
        Jwts.builder()
            .subject(user.getEmail())
            .claim("userId", user.getId())
            .claim("type", "refresh")
            .id(java.util.UUID.randomUUID().toString())
            .issuedAt(java.util.Date.from(Instant.now()))
            .expiration(java.util.Date.from(Instant.now().plusSeconds(3600)))
            .signWith(
                Keys.hmacShaKeyFor(
                    Decoders.BASE64.decode("b3RoZXItc2VjcmV0LWtleS1mb3ItdGVzdGluZy0xMjM0NTY3ODkw")))
            .compact();
    createRefreshSession(user, wrongSignatureToken, OffsetDateTime.now().plusDays(1), false);

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(wrongSignatureToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));
  }

  @Test
  void shouldRejectRefreshTokenMissingTypeClaim() throws Exception {
    UserEntity user = saveUser("missing-type@example.com", "secret123");
    String missingTypeToken =
        Jwts.builder()
            .subject(user.getEmail())
            .claim("userId", user.getId())
            .id(java.util.UUID.randomUUID().toString())
            .issuedAt(java.util.Date.from(Instant.now()))
            .expiration(java.util.Date.from(Instant.now().plusSeconds(3600)))
            .signWith(
                Keys.hmacShaKeyFor(
                    Decoders.BASE64.decode("dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW9ubHk=")))
            .compact();
    createRefreshSession(user, missingTypeToken, OffsetDateTime.now().plusDays(1), false);

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(missingTypeToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_INVALID"));
  }

  @Test
  void shouldAllowOnlyOneSuccessfulConcurrentRefreshForSameToken() throws Exception {
    saveUser("concurrent@example.com", "secret123");

    var loginResult =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email":"concurrent@example.com",
                          "password":"secret123"
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn();

    String refreshToken = extractCookieValue(loginResult.getResponse(), REFRESH_COOKIE_NAME);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Callable<Integer> refreshCall =
          () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
              throw new IllegalStateException("Timed out waiting to start refresh race");
            }
            return mockMvc
                .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(refreshToken)))
                .andReturn()
                .getResponse()
                .getStatus();
          };

      List<Future<Integer>> futures = new ArrayList<>();
      futures.add(executor.submit(refreshCall));
      futures.add(executor.submit(refreshCall));

      if (!ready.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out preparing refresh race");
      }
      start.countDown();

      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> future : futures) {
        statuses.add(future.get(5, TimeUnit.SECONDS));
      }

      org.assertj.core.api.Assertions.assertThat(statuses).containsExactlyInAnyOrder(200, 401);
      org.assertj.core.api.Assertions.assertThat(
              refreshTokenSessionRepository.findAll().stream()
                  .filter(session -> !session.isRevoked())
                  .count())
          .isEqualTo(1L);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void shouldExposeAuthEndpointsInIdentityOpenApiGroupWithoutBearerRequirement() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs/identity"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
        .andExpect(jsonPath("$.paths['/api/auth/register']").exists())
        .andExpect(jsonPath("$.paths['/api/auth/login'].post.security.length()").value(0))
        .andExpect(jsonPath("$.paths['/api/auth/register'].post.security.length()").value(0));
  }

  @Test
  void shouldExposeAuthSchemasWithExpectedFieldsInOpenApi() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs/identity"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.email").exists())
        .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.password").exists())
        .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.acceptTerms").exists())
        .andExpect(
            jsonPath("$.components.schemas.AuthTokenResponse.properties.accessToken").exists())
        .andExpect(jsonPath("$.components.schemas.AuthTokenResponse.properties.expiresAt").exists())
        .andExpect(
            jsonPath("$.components.schemas.AuthenticatedUserSummary.properties.email").exists())
        .andExpect(
            jsonPath("$.components.schemas.AuthenticatedUserSummary.properties.role").exists())
        .andExpect(
            jsonPath("$.components.schemas.RegisterRequest.properties.email.example")
                .value("user@example.com"))
        .andExpect(
            jsonPath("$.components.schemas.RegisterRequest.properties.password.example")
                .value("secret123"))
        .andExpect(
            jsonPath("$.components.schemas.RegisterRequest.properties.nationality.example")
                .value("VN"))
        .andExpect(
            jsonPath("$.paths['/api/auth/password-reset/request'].post.description")
                .value(org.hamcrest.Matchers.containsString("generic success message")))
        .andExpect(
            jsonPath("$.paths['/api/auth/register'].post.summary").value("Register a new account"))
        .andExpect(jsonPath("$.paths['/api/users/me']").exists())
        .andExpect(jsonPath("$.paths['/api/users/me/password']").exists())
        .andExpect(
            jsonPath("$.paths['/api/auth/logout'].post.summary").value("Logout current session"));
  }

  @Test
  void shouldUseExpectedSuccessWrapperAcrossAuthEndpoints() throws Exception {
    saveUser("wrapper@example.com", "secret123");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"wrapper@example.com",
                      "password":"secret123"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.message").value("Operation successful"));

    mockMvc
        .perform(
            post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"wrapper@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(
            jsonPath("$.message").value("If the email exists, a password reset link will be sent"));
  }

  @Test
  void shouldReturnCurrentUserProfileForAuthenticatedRequest() throws Exception {
    UserEntity user = saveUser("me@example.com", "secret123");
    user.setDob(OffsetDateTime.parse("1999-01-15T00:00:00Z"));
    user.setNationality("VN");
    user.setIdentifyNumber("ID-001");
    userRepository.save(user);
    String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getId());

    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value("me@example.com"))
        .andExpect(jsonPath("$.data.firstName").value("Jane"))
        .andExpect(jsonPath("$.data.phone").doesNotExist())
        .andExpect(jsonPath("$.data.dob").value("1999-01-15"))
        .andExpect(jsonPath("$.data.nationality").value("VN"))
        .andExpect(jsonPath("$.data.identifyNumber").value("ID-001"))
        .andExpect(jsonPath("$.message").value("Operation successful"));
  }

  @Test
  void shouldRequireAuthenticationForCurrentUserEndpoints() throws Exception {
    mockMvc
        .perform(get("/api/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

    mockMvc
        .perform(
            patch("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Alice\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

    mockMvc
        .perform(
            post("/api/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currentPassword":"secret123",
                      "newPassword":"newSecret123"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  void shouldPatchCurrentUserProfileWithoutChangingEmailOrPhone() throws Exception {
    UserEntity user = saveUser("patch@example.com", "secret123");
    user.setPhone("+84901234567");
    user.setNationality("TH");
    user.setIdentifyNumber("OLD-ID");
    userRepository.save(user);
    String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getId());

    mockMvc
        .perform(
            patch("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "firstName":"Alice",
                      "lastName":"Tran",
                      "dob":"1998-12-01",
                      "nationality":"VN",
                      "identifyNumber":"NEW-ID"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value("patch@example.com"))
        .andExpect(jsonPath("$.data.phone").value("+84901234567"))
        .andExpect(jsonPath("$.data.firstName").value("Alice"))
        .andExpect(jsonPath("$.data.lastName").value("Tran"))
        .andExpect(jsonPath("$.data.dob").value("1998-12-01"))
        .andExpect(jsonPath("$.data.nationality").value("VN"))
        .andExpect(jsonPath("$.data.identifyNumber").value("NEW-ID"))
        .andExpect(jsonPath("$.message").value("Profile updated successfully"));

    UserEntity updatedUser = userRepository.findById(user.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(updatedUser.getEmail())
        .isEqualTo("patch@example.com");
    org.assertj.core.api.Assertions.assertThat(updatedUser.getPhone()).isEqualTo("+84901234567");
    org.assertj.core.api.Assertions.assertThat(updatedUser.getFirstName()).isEqualTo("Alice");
    org.assertj.core.api.Assertions.assertThat(updatedUser.getLastName()).isEqualTo("Tran");
    org.assertj.core.api.Assertions.assertThat(updatedUser.getDob().toLocalDate())
        .isEqualTo(java.time.LocalDate.parse("1998-12-01"));
  }

  @Test
  void shouldValidateCurrentUserPatchPayload() throws Exception {
    UserEntity user = saveUser("patch-invalid@example.com", "secret123");
    String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getId());

    mockMvc
        .perform(
            patch("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "firstName":"   ",
                      "nationality":"VNM"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.error.details.firstName").isNotEmpty())
        .andExpect(jsonPath("$.error.details.nationality").isNotEmpty());
  }

  @Test
  void shouldChangeCurrentUserPasswordAndInvalidateOldTokens() throws Exception {
    UserEntity user = saveUser("change-password@example.com", "secret123");

    var loginResult =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email":"change-password@example.com",
                          "password":"secret123"
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn();

    String accessToken =
        extractJsonValue(loginResult.getResponse().getContentAsString(), "accessToken");
    String refreshToken = extractCookieValue(loginResult.getResponse(), REFRESH_COOKIE_NAME);

    mockMvc
        .perform(
            post("/api/users/me/password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currentPassword":"secret123",
                      "newPassword":"newSecret123"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.message").value("Password changed successfully"));

    mockMvc
        .perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

    mockMvc
        .perform(post("/api/auth/refresh").cookie(refreshTokenCookie(refreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REVOKED"));
  }

  @Test
  void shouldRejectPasswordChangeWhenCurrentPasswordIsIncorrect() throws Exception {
    UserEntity user = saveUser("wrong-current@example.com", "secret123");
    String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getId());

    mockMvc
        .perform(
            post("/api/users/me/password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currentPassword":"wrong-secret",
                      "newPassword":"newSecret123"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_INCORRECT"))
        .andExpect(jsonPath("$.path").value("/api/users/me/password"));
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

  private PasswordResetTokenEntity passwordResetToken(UserEntity user, String rawToken) {
    PasswordResetTokenEntity token = new PasswordResetTokenEntity();
    token.setUser(user);
    token.setTokenHash(tokenHashingService.hashToken(rawToken));
    token.setCreatedAt(OffsetDateTime.now());
    token.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
    token.setUsed(false);
    return token;
  }

  private RefreshTokenSessionEntity createRefreshSession(
      UserEntity user, String rawToken, boolean revoked) {
    return createRefreshSession(user, rawToken, OffsetDateTime.now().plusDays(1), revoked);
  }

  private RefreshTokenSessionEntity createRefreshSession(
      UserEntity user, String rawToken, OffsetDateTime expiresAt, boolean revoked) {
    RefreshTokenSessionEntity session = new RefreshTokenSessionEntity();
    OffsetDateTime now = OffsetDateTime.now();
    session.setUser(user);
    session.setRefreshTokenHash(tokenHashingService.hashToken(rawToken));
    session.setRevoked(revoked);
    session.setExpiresAt(expiresAt);
    session.setCreatedAt(now);
    session.setUpdatedAt(now);
    return refreshTokenSessionRepository.save(session);
  }

  private String customToken(
      String email, Long userId, String type, Instant issuedAt, Instant expiration) {
    return Jwts.builder()
        .subject(email)
        .claim("userId", userId)
        .claim("type", type)
        .id(java.util.UUID.randomUUID().toString())
        .issuedAt(java.util.Date.from(issuedAt))
        .expiration(java.util.Date.from(expiration))
        .signWith(
            Keys.hmacShaKeyFor(
                Decoders.BASE64.decode("dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW9ubHk=")))
        .compact();
  }

  private String extractJsonValue(String payload, String fieldName) {
    String marker = "\"" + fieldName + "\":\"";
    int start = payload.indexOf(marker);
    if (start < 0) {
      throw new IllegalStateException("Field not found: " + fieldName);
    }
    int valueStart = start + marker.length();
    int valueEnd = payload.indexOf('"', valueStart);
    return payload.substring(valueStart, valueEnd);
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
}
