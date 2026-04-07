package com.example.moduleidentity.application;

import com.example.common.exception.AppException;
import com.example.moduleidentity.domain.security.AuthRateLimiter;
import com.example.moduleidentity.domain.security.ClientKeyUtils;
import com.example.moduleidentity.domain.security.InputNormalizer;
import com.example.moduleidentity.domain.security.UserSecurityStateService;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.port.AuthTokenService;
import com.example.moduleidentity.port.ClientKeyResolver;
import com.example.moduleidentity.repository.UserRepository;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
  private final InputNormalizer inputNormalizer;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      AuthTokenService authTokenService,
      RefreshTokenSessionService refreshTokenSessionService,
      UserSecurityStateService userSecurityStateService,
      AuthRateLimiter authRateLimiter,
      ClientKeyResolver clientKeyResolver,
      InputNormalizer inputNormalizer) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.authTokenService = authTokenService;
    this.refreshTokenSessionService = refreshTokenSessionService;
    this.userSecurityStateService = userSecurityStateService;
    this.authRateLimiter = authRateLimiter;
    this.clientKeyResolver = clientKeyResolver;
    this.inputNormalizer = inputNormalizer;
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
      throw new AppException("TERMS_NOT_ACCEPTED", "You must accept the terms and conditions", 400);
    }
    String normalizedEmail =
        inputNormalizer.trimRequired(email, "EMAIL_REQUIRED", "Email is required");
    String normalizedFirstName =
        inputNormalizer.trimRequired(firstName, "FIRST_NAME_REQUIRED", "First name is required");
    String normalizedLastName =
        inputNormalizer.trimRequired(lastName, "LAST_NAME_REQUIRED", "Last name is required");
    String normalizedNationality = inputNormalizer.blankToNull(nationality);

    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new AppException("EMAIL_ALREADY_EXISTS", "Email is already registered", 409);
    }

    String normalizedPhone = normalizePhone(normalizedNationality, phone);
    if (normalizedPhone != null && userRepository.existsByPhone(normalizedPhone)) {
      throw new AppException("PHONE_ALREADY_EXISTS", "Phone number is already registered", 409);
    }

    OffsetDateTime now = OffsetDateTime.now();
    UserEntity user = new UserEntity();
    user.setEmail(normalizedEmail);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setFirstName(normalizedFirstName);
    user.setLastName(normalizedLastName);
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
            .orElseThrow(() -> invalidCredentials(normalizedEmail, normalizedClientKey));

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
    String normalizedRefreshToken = inputNormalizer.blankToNull(refreshToken);
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
    String normalizedRefreshToken = inputNormalizer.blankToNull(refreshToken);
    if (normalizedRefreshToken == null) {
      throw new AppException("REFRESH_TOKEN_REQUIRED", "Refresh token is required", 400);
    }

    refreshTokenSessionService.revokeByRefreshToken(normalizedRefreshToken);
  }

  @Transactional
  public UserEntity updateCurrentUserProfile(
      Long userId,
      String firstName,
      String lastName,
      LocalDate dob,
      String nationality,
      String identifyNumber) {
    UserEntity user = findById(userId);

    String normalizedFirstName =
        inputNormalizer.trimIfPresentNonBlank(
            firstName, "FIRST_NAME_INVALID", "First name must not be blank");
    String normalizedLastName =
        inputNormalizer.trimIfPresentNonBlank(
            lastName, "LAST_NAME_INVALID", "Last name must not be blank");
    String normalizedNationality = inputNormalizer.normalizeOptionalCountryCode(nationality);
    String normalizedIdentifyNumber = inputNormalizer.normalizeNullableString(identifyNumber);

    if (normalizedFirstName != null) {
      user.setFirstName(normalizedFirstName);
    }
    if (normalizedLastName != null) {
      user.setLastName(normalizedLastName);
    }
    if (dob != null) {
      user.setDob(dob.atStartOfDay().atOffset(ZoneOffset.UTC));
    }
    if (nationality != null) {
      user.setNationality(normalizedNationality);
    }
    if (identifyNumber != null) {
      user.setIdentifyNumber(normalizedIdentifyNumber);
    }

    user.setUpdatedAt(OffsetDateTime.now());
    return userRepository.save(user);
  }

  @Transactional
  public void changePassword(Long userId, String currentPassword, String newPassword) {
    UserEntity user = findById(userId);
    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new AppException("CURRENT_PASSWORD_INCORRECT", "Current password is incorrect", 401);
    }

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setUpdatedAt(OffsetDateTime.now());
    userRepository.save(user);
    userSecurityStateService.invalidateTokens(user);
    refreshTokenSessionService.revokeAllActiveByUserId(userId);
  }

  private AppException invalidCredentials(String normalizedEmail, String normalizedClientKey) {
    authRateLimiter.recordLoginFailure(normalizedEmail, normalizedClientKey);
    return new AppException("INVALID_CREDENTIALS", "Email or password is incorrect", 401);
  }

  private AppException invalidRefreshToken() {
    return new AppException("REFRESH_TOKEN_INVALID", "Refresh token is invalid", 401);
  }

  private String normalizeClientKey(String clientKey, String fallbackValue) {
    return ClientKeyUtils.normalizeClientKey(clientKey, clientKeyResolver, fallbackValue);
  }

  private String normalizePhone(String nationality, String phone) {
    String normalizedCountryCode = inputNormalizer.blankToNull(nationality);
    String normalizedPhone = inputNormalizer.blankToNull(phone);

    if (normalizedCountryCode == null && normalizedPhone == null) {
      return null;
    }

    if (normalizedCountryCode == null || normalizedPhone == null) {
      throw new AppException(
          "PHONE_COUNTRY_REQUIRED", "nationality and phone must be provided together", 400);
    }

    String region = inputNormalizer.normalizeOptionalCountryCode(normalizedCountryCode);

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
