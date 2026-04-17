package com.example.appbootstrap.controller.identity;

import com.example.appbootstrap.api.ApiSuccessMessage;
import com.example.appbootstrap.application.identity.PasswordResetEmailService;
import com.example.appbootstrap.config.AuthCookieProperties;
import com.example.appbootstrap.controller.identity.dto.AuthTokenResponse;
import com.example.appbootstrap.controller.identity.dto.AuthenticatedUserSummary;
import com.example.appbootstrap.controller.identity.dto.LoginRequest;
import com.example.appbootstrap.controller.identity.dto.PasswordResetConfirmRequest;
import com.example.appbootstrap.controller.identity.dto.PasswordResetRequest;
import com.example.appbootstrap.controller.identity.dto.RegisterRequest;
import com.example.appbootstrap.controller.identity.dto.RegisterResponse;
import com.example.common.exception.AppException;
import com.example.moduleidentity.application.AuthService;
import com.example.moduleidentity.application.AuthTokens;
import com.example.moduleidentity.application.PasswordResetService;
import com.example.moduleidentity.entity.UserEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Identity", description = "Authentication and password reset endpoints")
public class AuthController {

  private static final String REFRESH_COOKIE_REQUIRED = "Refresh token cookie is required";

  private final AuthService authService;
  private final PasswordResetService passwordResetService;
  private final PasswordResetEmailService passwordResetEmailService;
  private final AuthCookieProperties authCookieProperties;

  public AuthController(
      AuthService authService,
      PasswordResetService passwordResetService,
      PasswordResetEmailService passwordResetEmailService,
      AuthCookieProperties authCookieProperties) {
    this.authService = authService;
    this.passwordResetService = passwordResetService;
    this.passwordResetEmailService = passwordResetEmailService;
    this.authCookieProperties = authCookieProperties;
  }

  @PostMapping("/register")
  @ApiSuccessMessage("Registration successful")
  @Operation(
      summary = "Register a new account",
      description = "Creates a user account without logging in")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Registration successful"),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Email or phone already exists",
        content = @Content)
  })
  RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    UserEntity user =
        authService.register(
            request.email(),
            request.password(),
            request.firstName(),
            request.lastName(),
            Boolean.TRUE.equals(request.acceptTerms()),
            request.phone(),
            request.nationality());
    return new RegisterResponse(toUserSummary(user));
  }

  @PostMapping("/login")
  @Operation(
      summary = "Authenticate a user",
      description = "Returns a new access token and refresh token")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Authentication successful",
        content = @Content(schema = @Schema(implementation = AuthTokenResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content),
    @ApiResponse(responseCode = "429", description = "Too many login attempts", content = @Content)
  })
  AuthTokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    AuthTokens authTokens = authService.login(request.email(), request.password());
    setRefreshCookie(response, authTokens.refreshToken());
    return toAuthTokenResponse(authTokens);
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "Refresh access token",
      description =
          "Reads refresh token from HttpOnly cookie, rotates it, and returns a new access token")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Token refreshed successfully",
        content = @Content(schema = @Schema(implementation = AuthTokenResponse.class))),
    @ApiResponse(responseCode = "400", description = "Refresh cookie missing", content = @Content),
    @ApiResponse(responseCode = "401", description = "Refresh token invalid", content = @Content),
    @ApiResponse(
        responseCode = "429",
        description = "Too many refresh attempts",
        content = @Content)
  })
  AuthTokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
    String refreshToken = requireRefreshTokenCookie(request);
    AuthTokens authTokens = authService.refresh(refreshToken);
    setRefreshCookie(response, authTokens.refreshToken());
    return toAuthTokenResponse(authTokens);
  }

  @PostMapping("/logout")
  @ApiSuccessMessage("Logout successful")
  @Operation(
      summary = "Logout current session",
      description = "Revokes refresh session from HttpOnly cookie and clears the cookie")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Logout successful"),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
  })
  Void logout(HttpServletRequest request, HttpServletResponse response) {
    String refreshToken = extractRefreshTokenCookie(request);
    if (refreshToken != null) {
      authService.logout(refreshToken);
    }
    clearRefreshCookie(response);
    return null;
  }

  @PostMapping("/password-reset/request")
  @ApiSuccessMessage("If the email exists, a password reset code will be sent")
  @Operation(
      summary = "Request password reset",
      description =
          "Always returns a generic success message. The reset OTP is delivered out-of-band, typically by email.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Request accepted"),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "429",
        description = "Too many password reset requests",
        content = @Content)
  })
  Void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
    String otp = passwordResetService.requestPasswordReset(request.email());
    passwordResetEmailService.sendResetOtp(request.email(), otp);
    return null;
  }

  @PostMapping("/password-reset/confirm")
  @ApiSuccessMessage("Password reset successful")
  @Operation(
      summary = "Confirm password reset",
      description = "Consumes a reset OTP and updates the password")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Password reset successful"),
    @ApiResponse(
        responseCode = "400",
        description = "Validation failed or OTP invalid",
        content = @Content)
  })
  Void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
    passwordResetService.resetPassword(request.otp(), request.newPassword());
    return null;
  }

  private AuthTokenResponse toAuthTokenResponse(AuthTokens authTokens) {
    return new AuthTokenResponse(
        authTokens.accessToken(), authTokens.tokenType(), authTokens.expiresAt());
  }

  private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
    ResponseCookie.ResponseCookieBuilder cookieBuilder =
        ResponseCookie.from(authCookieProperties.name(), refreshToken)
            .httpOnly(authCookieProperties.httpOnly())
            .secure(authCookieProperties.secure())
            .path(authCookieProperties.path())
            .maxAge(authCookieProperties.maxAgeSeconds())
            .sameSite(authCookieProperties.sameSite());
    String domain = authCookieProperties.domain();
    if (domain != null && !domain.isBlank()) {
      cookieBuilder.domain(domain);
    }
    response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
  }

  private void clearRefreshCookie(HttpServletResponse response) {
    ResponseCookie.ResponseCookieBuilder cookieBuilder =
        ResponseCookie.from(authCookieProperties.name(), "")
            .httpOnly(authCookieProperties.httpOnly())
            .secure(authCookieProperties.secure())
            .path(authCookieProperties.path())
            .maxAge(0)
            .sameSite(authCookieProperties.sameSite());
    String domain = authCookieProperties.domain();
    if (domain != null && !domain.isBlank()) {
      cookieBuilder.domain(domain);
    }
    response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
  }

  private String requireRefreshTokenCookie(HttpServletRequest request) {
    String refreshToken = extractRefreshTokenCookie(request);
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new AppException("REFRESH_TOKEN_REQUIRED", REFRESH_COOKIE_REQUIRED, 400);
    }
    return refreshToken;
  }

  private String extractRefreshTokenCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null || cookies.length == 0) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (authCookieProperties.name().equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private AuthenticatedUserSummary toUserSummary(UserEntity user) {
    return new AuthenticatedUserSummary(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getPhone(),
        user.getNationality(),
        user.getRole(),
        user.getCreatedAt());
  }
}
