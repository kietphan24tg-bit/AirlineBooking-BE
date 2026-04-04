package com.example.moduleidentity.application;

import com.example.common.exception.AppException;
import com.example.moduleidentity.domain.security.AuthRateLimiter;
import com.example.moduleidentity.domain.security.UserSecurityStateService;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.port.AuthTokenService;
import com.example.moduleidentity.port.ClientKeyResolver;
import com.example.moduleidentity.repository.UserRepository;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthTokenService authTokenService;
  private final RefreshTokenSessionService refreshTokenSessionService;
  private final UserSecurityStateService userSecurityStateService;
  private final AuthRateLimiter authRateLimiter;
  private final ClientKeyResolver clientKeyResolver;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      AuthTokenService authTokenService,
      RefreshTokenSessionService refreshTokenSessionService,
      UserSecurityStateService userSecurityStateService,
      AuthRateLimiter authRateLimiter,
      ClientKeyResolver clientKeyResolver) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.authTokenService = authTokenService;
    this.refreshTokenSessionService = refreshTokenSessionService;
    this.userSecurityStateService = userSecurityStateService;
    this.authRateLimiter = authRateLimiter;
    this.clientKeyResolver = clientKeyResolver;
  }

  @Transactional
  public UserEntity register(
      String email,
      String password,
      String firstName,
      String lastName,
      boolean acceptTerms,
      String phone,
      String nationality) {
    if (!acceptTerms) {
      throw new AppException(
          "TERMS_NOT_ACCEPTED", "You must accept the terms and conditions", 400);
    }
    if (userRepository.existsByEmail(email.trim())) {
      throw new AppException("EMAIL_ALREADY_EXISTS", "Email is already registered", 409);
    }

    String normalizedNationality = blankToNull(nationality);
    String normalizedPhone = normalizePhone(normalizedNationality, phone);
    if (normalizedPhone != null && userRepository.existsByPhone(normalizedPhone)) {
      throw new AppException("PHONE_ALREADY_EXISTS", "Phone number is already registered", 409);
    }

    OffsetDateTime now = OffsetDateTime.now();
    UserEntity user = new UserEntity();
    user.setEmail(email.trim());
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setFirstName(firstName.trim());
    user.setLastName(lastName.trim());
    user.setPhone(normalizedPhone);
    user.setNationality(normalizedNationality);
    user.setAcceptTerms(true);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);

    return userRepository.save(user);
  }

  @Transactional
  public AuthTokens login(String email, String password) {
    return login(email, password, null);
  }

  @Transactional
  public AuthTokens login(String email, String password, String clientKey) {
    String normalizedEmail = email.trim();
    String normalizedClientKey = normalizeClientKey(clientKey, normalizedEmail);
    authRateLimiter.checkLoginAllowed(normalizedEmail, normalizedClientKey);
    UserEntity user =
        userRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(
                () -> invalidCredentials(normalizedEmail, normalizedClientKey));

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw invalidCredentials(normalizedEmail, normalizedClientKey);
    }

    authRateLimiter.resetLoginFailures(normalizedEmail, normalizedClientKey);

    String accessToken = authTokenService.generateAccessToken(user.getEmail(), user.getId());
    String refreshToken = authTokenService.generateRefreshToken(user.getEmail(), user.getId());
    refreshTokenSessionService.createSession(
        user, refreshToken, authTokenService.extractExpiration(refreshToken));

    return new AuthTokens(
        accessToken, refreshToken, "Bearer", authTokenService.extractExpiration(accessToken));
  }

  @Transactional
  public AuthTokens refresh(String refreshToken) {
    return refresh(refreshToken, null);
  }

  @Transactional
  public AuthTokens refresh(String refreshToken, String clientKey) {
    String normalizedRefreshToken = blankToNull(refreshToken);
    if (normalizedRefreshToken == null) {
      throw new AppException("REFRESH_TOKEN_REQUIRED", "Refresh token is required", 400);
    }
    String normalizedClientKey = normalizeClientKey(clientKey, "anonymous");
    authRateLimiter.consumeRefreshClientAttempt(normalizedClientKey);

    var session = refreshTokenSessionService.requireActiveSession(normalizedRefreshToken);
    UserEntity user = session.getUser();
    authRateLimiter.consumeRefreshUserAttempt(user.getId());

    try {
      if (!authTokenService.isRefreshToken(normalizedRefreshToken)) {
        throw invalidRefreshToken();
      }

      if (!authTokenService.isTokenValid(normalizedRefreshToken, user.getEmail())) {
        throw invalidRefreshToken();
      }

      Long tokenUserId = authTokenService.extractUserId(normalizedRefreshToken);
      if (tokenUserId == null || !tokenUserId.equals(user.getId())) {
        throw invalidRefreshToken();
      }

      if (userSecurityStateService.isTokenIssuedBeforeInvalidation(
          user, authTokenService.extractIssuedAt(normalizedRefreshToken))) {
        throw invalidRefreshToken();
      }
    } catch (AppException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw invalidRefreshToken();
    }

    String newAccessToken = authTokenService.generateAccessToken(user.getEmail(), user.getId());
    String newRefreshToken = authTokenService.generateRefreshToken(user.getEmail(), user.getId());

    // Revoke atomically so concurrent refresh attempts cannot rotate the same session twice.
    if (!refreshTokenSessionService.revokeIfActive(session.getId())) {
      throw new AppException("REFRESH_TOKEN_REVOKED", "Refresh token has been revoked", 401);
    }
    refreshTokenSessionService.createSession(
        user, newRefreshToken, authTokenService.extractExpiration(newRefreshToken));

    return new AuthTokens(
        newAccessToken,
        newRefreshToken,
        "Bearer",
        authTokenService.extractExpiration(newAccessToken));
  }

  @Transactional
  public void logout(String refreshToken) {
    String normalizedRefreshToken = blankToNull(refreshToken);
    if (normalizedRefreshToken == null) {
      throw new AppException("REFRESH_TOKEN_REQUIRED", "Refresh token is required", 400);
    }

    refreshTokenSessionService.revokeByRefreshToken(normalizedRefreshToken);
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private AppException invalidCredentials(String normalizedEmail, String normalizedClientKey) {
    authRateLimiter.recordLoginFailure(normalizedEmail, normalizedClientKey);
    return new AppException("INVALID_CREDENTIALS", "Email or password is incorrect", 401);
  }

  private AppException invalidRefreshToken() {
    return new AppException("REFRESH_TOKEN_INVALID", "Refresh token is invalid", 401);
  }

  private String normalizeClientKey(String clientKey, String fallbackValue) {
    String explicitClientKey = blankToNull(clientKey);
    if (explicitClientKey != null) {
      return explicitClientKey;
    }

    String resolvedClientKey = blankToNull(clientKeyResolver.resolveClientKey());
    return resolvedClientKey == null ? fallbackValue : resolvedClientKey;
  }

  private static String normalizePhone(String nationality, String phone) {
    String normalizedCountryCode = blankToNull(nationality);
    String normalizedPhone = blankToNull(phone);

    if (normalizedCountryCode == null && normalizedPhone == null) {
      return null;
    }

    if (normalizedCountryCode == null || normalizedPhone == null) {
      throw new AppException(
          "PHONE_COUNTRY_REQUIRED", "nationality and phone must be provided together", 400);
    }

    String region = normalizedCountryCode.toUpperCase(Locale.ROOT);
    if (region.length() != 2 || !region.chars().allMatch(Character::isLetter)) {
      throw new AppException("INVALID_COUNTRY_CODE", "nationality must be ISO alpha-2", 400);
    }

    try {
      var parsed = PHONE_NUMBER_UTIL.parse(normalizedPhone, region);
      if (!PHONE_NUMBER_UTIL.isValidNumberForRegion(parsed, region)) {
        throw new AppException(
            "INVALID_PHONE_NUMBER", "Phone number is invalid for selected country", 400);
      }
      return PHONE_NUMBER_UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
    } catch (NumberParseException ex) {
      throw new AppException("INVALID_PHONE_NUMBER", "Phone number format is invalid", 400);
    }
  }

  @Transactional(readOnly = true)
  public UserEntity findByEmail(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(
            () -> new AppException("USER_NOT_FOUND", "User not found with email: " + email, 404));
  }

  @Transactional(readOnly = true)
  public UserEntity findById(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(
            () -> new AppException("USER_NOT_FOUND", "User not found with id: " + id, 404));
  }
}
