package com.example.moduleidentity.application;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleidentity.domain.security.AuthRateLimiter;
import com.example.moduleidentity.domain.security.TokenHashingService;
import com.example.moduleidentity.domain.security.UserSecurityStateService;
import com.example.moduleidentity.entity.PasswordResetTokenEntity;
import com.example.moduleidentity.entity.Role;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.port.ClientKeyResolver;
import com.example.moduleidentity.port.RateLimitStore;
import com.example.moduleidentity.repository.PasswordResetTokenRepository;
import com.example.moduleidentity.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private RefreshTokenSessionService refreshTokenSessionService;
  @Mock private TokenHashingService tokenHashingService;
  @Mock private UserSecurityStateService userSecurityStateService;
  @Mock private RateLimitStore rateLimitStore;
  @Mock private ClientKeyResolver clientKeyResolver;

  private PasswordResetService passwordResetService;
  private AuthRateLimiter authRateLimiter;
  private final java.util.Map<String, Long> counts = new java.util.HashMap<>();

  @BeforeEach
  void setUp() {
    authRateLimiter =
        new AuthRateLimiter(
            rateLimitStore,
            new com.example.moduleidentity.config.RateLimitProperties(),
            new TokenHashingService(),
            new SimpleMeterRegistry());
    passwordResetService =
        new PasswordResetService(
            userRepository,
            passwordResetTokenRepository,
            passwordEncoder,
            refreshTokenSessionService,
            tokenHashingService,
            userSecurityStateService,
            authRateLimiter,
            clientKeyResolver);
    when(clientKeyResolver.resolveClientKey()).thenReturn("ip-1");
    when(rateLimitStore.getCount(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> counts.getOrDefault(invocation.getArgument(0, String.class), 0L));
    when(
            rateLimitStore.increment(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0, String.class);
              Long current = counts.compute(key, (ignored, value) -> value == null ? 1L : value + 1L);
              return current;
            });
  }

  @Test
  void shouldInvalidateUserTokensAndRevokeRefreshSessionsWhenResettingPassword() {
    UserEntity user = new UserEntity();
    user.setId(7L);
    user.setEmail("user@example.com");
    user.setRole(Role.USER);

    PasswordResetTokenEntity token = new PasswordResetTokenEntity();
    token.setUser(user);
    token.setUsed(false);
    token.setExpiresAt(OffsetDateTime.now().plusMinutes(5));

    when(tokenHashingService.hashToken("raw-token")).thenReturn("hashed");
    when(passwordResetTokenRepository.findByTokenHash("hashed"))
        .thenReturn(java.util.Optional.of(token));
    when(passwordEncoder.encode("new-secret")).thenReturn("encoded-secret");

    passwordResetService.resetPassword("raw-token", "new-secret");

    verify(userSecurityStateService).invalidateTokens(user);
    verify(refreshTokenSessionService).revokeAllActiveByUserId(7L);
    verify(passwordResetTokenRepository).save(token);
    assertTrue(token.isUsed());
  }

  @Test
  void shouldReturnTooManyPasswordResetRequestsAfterThreshold() {
    for (int i = 0; i < 3; i++) {
      passwordResetService.requestPasswordReset("user@example.com", "ip-1");
    }

    org.junit.jupiter.api.Assertions.assertThrows(
        com.example.common.exception.AppException.class,
        () -> passwordResetService.requestPasswordReset("user@example.com", "ip-1"));
  }
}
