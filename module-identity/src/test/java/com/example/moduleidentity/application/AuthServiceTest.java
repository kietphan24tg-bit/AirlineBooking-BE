package com.example.moduleidentity.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.moduleidentity.domain.security.AuthRateLimiter;
import com.example.moduleidentity.domain.security.InputNormalizer;
import com.example.moduleidentity.domain.security.TokenHashingService;
import com.example.moduleidentity.domain.security.UserSecurityStateService;
import com.example.moduleidentity.entity.RefreshTokenSessionEntity;
import com.example.moduleidentity.entity.Role;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.port.AuthTokenService;
import com.example.moduleidentity.port.ClientKeyResolver;
import com.example.moduleidentity.port.RateLimitStore;
import com.example.moduleidentity.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AuthTokenService authTokenService;
  @Mock private RefreshTokenSessionService refreshTokenSessionService;
  @Mock private UserSecurityStateService userSecurityStateService;
  @Mock private RateLimitStore rateLimitStore;
  @Mock private ClientKeyResolver clientKeyResolver;

  private AuthService authService;
  private AuthRateLimiter authRateLimiter;
  private final java.util.Map<String, Long> counts = new java.util.HashMap<>();

  @BeforeEach
  void setUp() {
    var rateLimitProperties = new com.example.moduleidentity.config.RateLimitProperties();
    rateLimitProperties.getRefresh().setClientLimit(2);
    rateLimitProperties.getRefresh().setUserLimit(2);
    authRateLimiter =
        new AuthRateLimiter(
            rateLimitStore,
            rateLimitProperties,
            new TokenHashingService(),
            new SimpleMeterRegistry());
    authService =
        new AuthService(
            userRepository,
            passwordEncoder,
            authTokenService,
            refreshTokenSessionService,
            userSecurityStateService,
            authRateLimiter,
            clientKeyResolver,
            new InputNormalizer());
    when(clientKeyResolver.resolveClientKey()).thenReturn("ip-1");
    when(rateLimitStore.getCount(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(invocation -> counts.getOrDefault(invocation.getArgument(0, String.class), 0L));
    when(rateLimitStore.increment(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0, String.class);
              Long current =
                  counts.compute(key, (ignored, value) -> value == null ? 1L : value + 1L);
              return current;
            });
    org.mockito.Mockito.doAnswer(
            invocation -> {
              counts.remove(invocation.getArgument(0, String.class));
              return null;
            })
        .when(rateLimitStore)
        .delete(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void shouldRejectRefreshTokenIssuedBeforeUserInvalidation() {
    UserEntity user = user();
    user.setTokenInvalidBefore(OffsetDateTime.parse("2026-04-03T10:00:00Z"));

    RefreshTokenSessionEntity session = new RefreshTokenSessionEntity();
    session.setUser(user);

    when(refreshTokenSessionService.requireActiveSession("refresh-token")).thenReturn(session);
    when(authTokenService.isRefreshToken("refresh-token")).thenReturn(true);
    when(authTokenService.isTokenValid("refresh-token", user.getEmail())).thenReturn(true);
    when(authTokenService.extractUserId("refresh-token")).thenReturn(user.getId());
    when(authTokenService.extractIssuedAt("refresh-token"))
        .thenReturn(OffsetDateTime.parse("2026-04-03T09:59:59Z"));
    when(userSecurityStateService.isTokenIssuedBeforeInvalidation(
            user, OffsetDateTime.parse("2026-04-03T09:59:59Z")))
        .thenReturn(true);

    AppException exception =
        assertThrows(AppException.class, () -> authService.refresh("refresh-token"));

    org.junit.jupiter.api.Assertions.assertEquals("REFRESH_TOKEN_INVALID", exception.getCode());
  }

  @Test
  void shouldReturnTooManyLoginAttemptsAfterRepeatedFailures() {
    UserEntity user = user();
    when(userRepository.findByEmail("user@example.com")).thenReturn(java.util.Optional.of(user));
    when(passwordEncoder.matches("bad-password", user.getPasswordHash())).thenReturn(false);

    for (int i = 0; i < 5; i++) {
      assertThrows(
          AppException.class, () -> authService.login("user@example.com", "bad-password", "ip-1"));
    }

    AppException exception =
        assertThrows(
            AppException.class,
            () -> authService.login("user@example.com", "bad-password", "ip-1"));

    org.junit.jupiter.api.Assertions.assertEquals("TOO_MANY_LOGIN_ATTEMPTS", exception.getCode());
  }

  @Test
  void shouldReturnTooManyRefreshAttemptsAfterThreshold() {
    UserEntity user = user();
    RefreshTokenSessionEntity session = new RefreshTokenSessionEntity();
    session.setId(10L);
    session.setUser(user);

    when(refreshTokenSessionService.requireActiveSession("refresh-token")).thenReturn(session);
    when(refreshTokenSessionService.revokeIfActive(10L)).thenReturn(true);
    when(authTokenService.isRefreshToken("refresh-token")).thenReturn(true);
    when(authTokenService.isTokenValid("refresh-token", user.getEmail())).thenReturn(true);
    when(authTokenService.extractUserId("refresh-token")).thenReturn(user.getId());
    when(authTokenService.extractIssuedAt("refresh-token"))
        .thenReturn(OffsetDateTime.parse("2026-04-03T10:00:01Z"));
    when(authTokenService.generateAccessToken(user.getEmail(), user.getId())).thenReturn("access");
    when(authTokenService.generateRefreshToken(user.getEmail(), user.getId()))
        .thenReturn("refresh");
    when(authTokenService.extractExpiration("access"))
        .thenReturn(OffsetDateTime.parse("2026-04-03T10:15:01Z"));
    when(authTokenService.extractExpiration("refresh"))
        .thenReturn(OffsetDateTime.parse("2026-04-04T10:00:01Z"));
    when(userSecurityStateService.isTokenIssuedBeforeInvalidation(
            user, OffsetDateTime.parse("2026-04-03T10:00:01Z")))
        .thenReturn(false);

    authService.refresh("refresh-token", "ip-1");
    authService.refresh("refresh-token", "ip-1");

    AppException exception =
        assertThrows(AppException.class, () -> authService.refresh("refresh-token", "ip-1"));

    org.junit.jupiter.api.Assertions.assertEquals("TOO_MANY_REFRESH_ATTEMPTS", exception.getCode());
  }

  @Test
  void shouldUpdateCurrentUserProfileWithoutChangingEmailOrPhone() {
    UserEntity user = user();
    user.setPhone("+84901234567");
    user.setDob(OffsetDateTime.parse("2000-01-01T00:00:00Z"));
    when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
    when(userRepository.save(user)).thenReturn(user);

    UserEntity updated =
        authService.updateCurrentUserProfile(
            1L, "  Alice  ", "  Nguyen  ", LocalDate.parse("1999-02-03"), "vn", "  ID-123  ");

    org.junit.jupiter.api.Assertions.assertEquals("Alice", updated.getFirstName());
    org.junit.jupiter.api.Assertions.assertEquals("Nguyen", updated.getLastName());
    org.junit.jupiter.api.Assertions.assertEquals("VN", updated.getNationality());
    org.junit.jupiter.api.Assertions.assertEquals("ID-123", updated.getIdentifyNumber());
    org.junit.jupiter.api.Assertions.assertEquals("user@example.com", updated.getEmail());
    org.junit.jupiter.api.Assertions.assertEquals("+84901234567", updated.getPhone());
    org.junit.jupiter.api.Assertions.assertEquals(
        LocalDate.parse("1999-02-03"), updated.getDob().toLocalDate());
  }

  @Test
  void shouldChangePasswordAndInvalidateExistingTokens() {
    UserEntity user = user();
    when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
    when(passwordEncoder.matches("old-secret", user.getPasswordHash())).thenReturn(true);
    when(passwordEncoder.encode("new-secret")).thenReturn("new-encoded");
    when(userRepository.save(user)).thenReturn(user);

    authService.changePassword(1L, "old-secret", "new-secret");

    org.junit.jupiter.api.Assertions.assertEquals("new-encoded", user.getPasswordHash());
    org.mockito.Mockito.verify(userSecurityStateService).invalidateTokens(user);
    org.mockito.Mockito.verify(refreshTokenSessionService).revokeAllActiveByUserId(1L);
  }

  @Test
  void shouldRejectPasswordChangeWhenCurrentPasswordIsIncorrect() {
    UserEntity user = user();
    when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
    when(passwordEncoder.matches("wrong-secret", user.getPasswordHash())).thenReturn(false);

    AppException exception =
        assertThrows(
            AppException.class, () -> authService.changePassword(1L, "wrong-secret", "new-secret"));

    org.junit.jupiter.api.Assertions.assertEquals(
        "CURRENT_PASSWORD_INCORRECT", exception.getCode());
  }

  private static UserEntity user() {
    UserEntity user = new UserEntity();
    user.setId(1L);
    user.setEmail("user@example.com");
    user.setPasswordHash("encoded");
    user.setRole(Role.USER);
    user.setCreatedAt(OffsetDateTime.parse("2026-04-03T09:00:00Z"));
    user.setUpdatedAt(OffsetDateTime.parse("2026-04-03T09:00:00Z"));
    return user;
  }
}
