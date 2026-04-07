package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    name = "ChangePasswordRequest",
    description = "Password change payload for the current user")
@SuppressWarnings("java:S1192")
public record ChangePasswordRequest(
    @Schema(example = "secret123") @NotBlank(message = CURRENT_PASSWORD_REQUIRED_MESSAGE) String currentPassword,
    @Schema(example = "newSecret123")
        @NotBlank(message = NEW_PASSWORD_REQUIRED_MESSAGE) @Size(min = 6, message = NEW_PASSWORD_MIN_LENGTH_MESSAGE) String newPassword) {

  private static final String CURRENT_PASSWORD_REQUIRED_MESSAGE = "Current password is required";
  private static final String NEW_PASSWORD_REQUIRED_MESSAGE = "New password is required";
  private static final String NEW_PASSWORD_MIN_LENGTH_MESSAGE =
      "New password must have at least 6 characters";
}
