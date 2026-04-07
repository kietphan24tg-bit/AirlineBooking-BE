package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    name = "PasswordResetConfirmRequest",
    description = "Reset token and new password used to confirm password reset")
@SuppressWarnings("java:S1192")
public record PasswordResetConfirmRequest(
    @Schema(example = "reset-token") @NotBlank(message = TOKEN_REQ_MSG) String token,
    @Schema(example = "newSecret123")
        @NotBlank(message = NEW_PWD_REQ_MSG) @Size(min = 8, max = 128, message = NEW_PWD_LEN_MSG) String newPassword) {

  private static final String TOKEN_REQ_MSG = "Token is required";
  private static final String NEW_PWD_REQ_MSG = "New password is required";
  private static final String NEW_PWD_LEN_MSG = "New password must have at least 8 characters";
}
