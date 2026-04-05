package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    name = "PasswordResetConfirmRequest",
    description = "Reset token and new password used to confirm password reset")
@SuppressWarnings("java:S1192")
public record PasswordResetConfirmRequest(
    @Schema(example = "reset-token") @NotBlank(message = TOKEN_REQUIRED_MESSAGE) String token,
    @Schema(example = "newSecret123")
        @NotBlank(message = NEW_PASSWORD_REQUIRED_MESSAGE) @Size(min = 6, message = NEW_PASSWORD_MIN_LENGTH_MESSAGE) String newPassword) {

  private static final String TOKEN_REQUIRED_MESSAGE = "Token is required";
  private static final String NEW_PASSWORD_REQUIRED_MESSAGE = "New password is required";
  private static final String NEW_PASSWORD_MIN_LENGTH_MESSAGE =
      "New password must have at least 6 characters";
}
