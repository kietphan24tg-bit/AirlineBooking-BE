package com.example.appbootstrap.controller.identity;

import com.example.appbootstrap.api.ApiSuccessMessage;
import com.example.appbootstrap.controller.identity.dto.AuthTokenResponse;
import com.example.appbootstrap.controller.identity.dto.AuthenticatedUserSummary;
import com.example.appbootstrap.controller.identity.dto.LoginRequest;
import com.example.appbootstrap.controller.identity.dto.LogoutRequest;
import com.example.appbootstrap.controller.identity.dto.PasswordResetConfirmRequest;
import com.example.appbootstrap.controller.identity.dto.PasswordResetRequest;
import com.example.appbootstrap.controller.identity.dto.RefreshTokenRequest;
import com.example.appbootstrap.controller.identity.dto.RegisterRequest;
import com.example.appbootstrap.controller.identity.dto.RegisterResponse;
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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Identity", description = "Authentication and password reset endpoints")
public class AuthController {

  private final AuthService authService;
  private final PasswordResetService passwordResetService;

  public AuthController(AuthService authService, PasswordResetService passwordResetService) {
    this.authService = authService;
    this.passwordResetService = passwordResetService;
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
  AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
    return toAuthTokenResponse(authService.login(request.email(), request.password()));
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "Refresh access token",
      description = "Rotates refresh token and returns a new token pair")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Tokens refreshed successfully",
        content = @Content(schema = @Schema(implementation = AuthTokenResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(responseCode = "401", description = "Refresh token invalid", content = @Content),
    @ApiResponse(
        responseCode = "429",
        description = "Too many refresh attempts",
        content = @Content)
  })
  AuthTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return toAuthTokenResponse(authService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  @ApiSuccessMessage("Logout successful")
  @Operation(
      summary = "Logout current session",
      description = "Revokes the provided refresh token session")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Logout successful"),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
  })
  Void logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request.refreshToken());
    return null;
  }

  @PostMapping("/password-reset/request")
  @ApiSuccessMessage("If the email exists, a password reset link will be sent")
  @Operation(
      summary = "Request password reset",
      description =
          "Always returns a generic success message. The reset token is delivered out-of-band, typically by email.")
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
    passwordResetService.requestPasswordReset(request.email());
    return null;
  }

  @PostMapping("/password-reset/confirm")
  @ApiSuccessMessage("Password reset successful")
  @Operation(
      summary = "Confirm password reset",
      description = "Consumes a reset token and updates the password")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Password reset successful"),
    @ApiResponse(
        responseCode = "400",
        description = "Validation failed or token invalid",
        content = @Content)
  })
  Void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
    passwordResetService.resetPassword(request.token(), request.newPassword());
    return null;
  }

  private AuthTokenResponse toAuthTokenResponse(AuthTokens authTokens) {
    return new AuthTokenResponse(
        authTokens.accessToken(),
        authTokens.refreshToken(),
        authTokens.tokenType(),
        authTokens.expiresAt());
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
