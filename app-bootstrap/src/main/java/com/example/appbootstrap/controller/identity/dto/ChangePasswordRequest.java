package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ChangePasswordRequest", description = "Password change payload for the current user")
public record ChangePasswordRequest(
    @Schema(example = "secret123")
    @NotBlank(message = "Current password is required")
    String currentPassword,
    @Schema(example = "newSecret123")
    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "New password must have at least 6 characters")
    String newPassword) {}
