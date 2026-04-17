package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(
    name = "PasswordResetConfirmRequest",
    description = "Reset OTP and new password used to confirm password reset")
@SuppressWarnings("java:S1192")
public record PasswordResetConfirmRequest(
    @Schema(example = "123456")
        @NotBlank(message = OTP_REQ_MSG) @Pattern(regexp = "\\d{6}", message = OTP_FORMAT_MSG) String otp,
    @Schema(example = "newSecret123")
        @NotBlank(message = NEW_PWD_REQ_MSG) @Size(min = 8, max = 128, message = NEW_PWD_LEN_MSG) String newPassword) {

  private static final String OTP_REQ_MSG = "OTP is required";
  private static final String OTP_FORMAT_MSG = "OTP must be 6 digits";
  private static final String NEW_PWD_REQ_MSG = "New password is required";
  private static final String NEW_PWD_LEN_MSG = "New password must have at least 8 characters";
}
