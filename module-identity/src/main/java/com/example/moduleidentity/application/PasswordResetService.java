package com.example.moduleidentity.application;

import com.example.common.exception.AppException;
import com.example.moduleidentity.domain.security.AuthRateLimiter;
import com.example.moduleidentity.domain.security.ClientKeyUtils;
import com.example.moduleidentity.domain.security.TokenHashingService;
import com.example.moduleidentity.domain.security.UserSecurityStateService;
import com.example.moduleidentity.entity.PasswordResetTokenEntity;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.port.ClientKeyResolver;
import com.example.moduleidentity.repository.PasswordResetTokenRepository;
import com.example.moduleidentity.repository.UserRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

  private static final int OTP_BOUND = 1_000_000;
  private static final int OTP_RETRY_LIMIT = 5;
  private static final long EXPIRATION_MINUTES = 15;
  private static final int PASSWORD_MIN_LENGTH = 8;
  private static final int PASSWORD_MAX_LENGTH = 128;
  private static final String OTP_PATTERN = "\\d{6}";

  private final UserRepository userRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenSessionService refreshTokenSessionService;
  private final TokenHashingService tokenHashingService;
  private final UserSecurityStateService userSecurityStateService;
  private final AuthRateLimiter authRateLimiter;
  private final ClientKeyResolver clientKeyResolver;
  private final SecureRandom secureRandom = new SecureRandom();

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
   * Generate a password reset OTP for the given email.
   *
   * <p>Flow:
   *
   * <ul>
   *   <li>If email not found: do nothing but still return normally (prevent user enumeration).
   *   <li>If found: delete previous OTPs, create new OTP with 15 minutes TTL, store hash in DB.
   *   <li>Return raw OTP so controller can deliver it via email.
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

    // Remove existing OTPs for this user
    passwordResetTokenRepository.deleteByUser_Id(user.getId());

    String otp = generateUniqueOtp();
    String tokenHash = tokenHashingService.hashToken(otp);
    OffsetDateTime now = OffsetDateTime.now();

    PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
    entity.setUser(user);
    entity.setTokenHash(tokenHash);
    entity.setExpiresAt(now.plus(EXPIRATION_MINUTES, ChronoUnit.MINUTES));
    entity.setUsed(false);
    entity.setCreatedAt(now);

    passwordResetTokenRepository.save(entity);

    return otp;
  }

  @Transactional
  public void resetPassword(String otp, String newPassword) {
    validateResetPasswordInput(otp, newPassword);
    authRateLimiter.consumePasswordResetConfirmAttempt(
        normalizeClientKey(null, "password-reset-confirm"));
    String tokenHash = tokenHashingService.hashToken(otp);
    PasswordResetTokenEntity token =
        passwordResetTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    new AppException(
                        "RESET_OTP_INVALID", "Password reset OTP is invalid or expired", 400));

    if (token.isUsed() || !token.getExpiresAt().isAfter(OffsetDateTime.now())) {
      throw new AppException("RESET_OTP_INVALID", "Password reset OTP is invalid or expired", 400);
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

  private String generateUniqueOtp() {
    for (int attempt = 0; attempt < OTP_RETRY_LIMIT; attempt++) {
      String otp = "%06d".formatted(secureRandom.nextInt(OTP_BOUND));
      if (!passwordResetTokenRepository.existsByTokenHash(tokenHashingService.hashToken(otp))) {
        return otp;
      }
    }
    throw new AppException(
        "RESET_OTP_GENERATION_FAILED", "Unable to generate password reset OTP", 500);
  }

  private void validateResetPasswordInput(String otp, String newPassword) {
    if (otp == null || otp.isBlank()) {
      throw new AppException("RESET_OTP_REQUIRED", "Reset OTP is required", 400);
    }
    if (!otp.matches(OTP_PATTERN)) {
      throw new AppException("RESET_OTP_INVALID", "Reset OTP must be 6 digits", 400);
    }
    if (newPassword == null || newPassword.isBlank()) {
      throw new AppException("NEW_PASSWORD_REQUIRED", "New password is required", 400);
    }
    int length = newPassword.length();
    if (length < PASSWORD_MIN_LENGTH || length > PASSWORD_MAX_LENGTH) {
      throw new AppException(
          "NEW_PASSWORD_INVALID_LENGTH",
          "New password must be between "
              + PASSWORD_MIN_LENGTH
              + " and "
              + PASSWORD_MAX_LENGTH
              + " characters",
          400);
    }
  }

  private String normalizeClientKey(String clientKey, String normalizedEmail) {
    return ClientKeyUtils.normalizeClientKey(clientKey, clientKeyResolver, normalizedEmail);
  }
}
