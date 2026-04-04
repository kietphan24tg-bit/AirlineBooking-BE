package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "PasswordResetConfirmRequest", description = "Reset token and new password used to confirm password reset")
public record PasswordResetConfirmRequest(
    @Schema(example = "vV7Q4mGqJjw0nG0o3QkW0nL3Qm1gS3eA")
    @NotBlank(message = "Token is required")
    String token,
    @Schema(example = "newSecret123")
    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "New password must have at least 6 characters")
    String newPassword) {}
