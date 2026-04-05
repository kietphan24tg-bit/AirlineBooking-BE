package com.example.moduleidentity.application;

import com.example.common.exception.AppException;
import com.example.moduleidentity.domain.security.AuthRateLimiter;
import com.example.moduleidentity.domain.security.TokenHashingService;
import com.example.moduleidentity.domain.security.UserSecurityStateService;
import com.example.moduleidentity.entity.PasswordResetTokenEntity;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.port.ClientKeyResolver;
import com.example.moduleidentity.repository.PasswordResetTokenRepository;
import com.example.moduleidentity.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

  private static final int RAW_TOKEN_BYTES = 32; // 256-bit
  private static final long EXPIRATION_MINUTES = 15;

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenSessionService refreshTokenSessionService;
  private final TokenHashingService tokenHashingService;
  private final UserSecurityStateService userSecurityStateService;
  private final AuthRateLimiter authRateLimiter;
  private final ClientKeyResolver clientKeyResolver;

  public PasswordResetService(
      UserRepository userRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      PasswordEncoder passwordEncoder,
      RefreshTokenSessionService refreshTokenSessionService,
      TokenHashingService tokenHashingService,
      UserSecurityStateService userSecurityStateService,
      AuthRateLimiter authRateLimiter,
      ClientKeyResolver clientKeyResolver) {
    this.userRepository = userRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.refreshTokenSessionService = refreshTokenSessionService;
    this.tokenHashingService = tokenHashingService;
    this.userSecurityStateService = userSecurityStateService;
    this.authRateLimiter = authRateLimiter;
    this.clientKeyResolver = clientKeyResolver;
  }

  /**
   * Generate a password reset token for the given email.
   *
   * <p>Flow:
   *
   * <ul>
   *   <li>If email not found: do nothing but still return normally (prevent user enumeration).
   *   <li>If found: delete previous tokens, create new token with 15 minutes TTL, store hash in DB.
   *   <li>Return raw token so controller can include it in reset link sent via email.
   * </ul>
   */
  @Transactional
  public String requestPasswordReset(String email) {
    return requestPasswordReset(email, null);
  }

  @Transactional
  public String requestPasswordReset(String email, String clientKey) {
    String normalizedEmail = email.trim();
    authRateLimiter.consumePasswordResetAttempt(
        normalizedEmail, normalizeClientKey(clientKey, normalizedEmail));
    UserEntity user = userRepository.findByEmail(normalizedEmail).orElse(null);

    // To avoid user enumeration, always behave as if request is successful.
    if (user == null) {
      return null;
    }

    // Remove existing tokens for this user
    passwordResetTokenRepository.deleteByUser_Id(user.getId());

    // Generate new token
    String rawToken = tokenHashingService.generateUrlSafeToken(RAW_TOKEN_BYTES);
    String tokenHash = tokenHashingService.hashToken(rawToken);
    OffsetDateTime now = OffsetDateTime.now();

    PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
    entity.setUser(user);
    entity.setTokenHash(tokenHash);
    entity.setExpiresAt(now.plus(EXPIRATION_MINUTES, ChronoUnit.MINUTES));
    entity.setUsed(false);
    entity.setCreatedAt(now);

    passwordResetTokenRepository.save(entity);

    return rawToken;
  }

  @Transactional
  public void resetPassword(String rawToken, String newPassword) {
    String tokenHash = tokenHashingService.hashToken(rawToken);
    PasswordResetTokenEntity token =
        passwordResetTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    new AppException(
                        "RESET_TOKEN_INVALID", "Password reset token is invalid or expired", 400));

    if (token.isUsed() || token.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw new AppException(
          "RESET_TOKEN_INVALID", "Password reset token is invalid or expired", 400);
    }

    UserEntity user = token.getUser();

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userSecurityStateService.invalidateTokens(user);

    token.setUsed(true);
    token.setUsedAt(OffsetDateTime.now());
    passwordResetTokenRepository.save(token);

    // Revoke all existing refresh token sessions for this user so old tokens cannot be used
    refreshTokenSessionService.revokeAllActiveByUserId(user.getId());
  }

  private String normalizeClientKey(String clientKey, String normalizedEmail) {
    if (clientKey != null && !clientKey.trim().isEmpty()) {
      return clientKey.trim();
    }
    String resolvedClientKey = clientKeyResolver.resolveClientKey();
    if (resolvedClientKey != null && !resolvedClientKey.trim().isEmpty()) {
      return resolvedClientKey.trim();
    }
    return normalizedEmail;
  }
}
