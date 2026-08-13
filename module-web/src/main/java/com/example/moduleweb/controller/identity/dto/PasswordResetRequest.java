package com.example.moduleweb.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "PasswordResetRequest", description = "Email used to request a password reset")
public record PasswordResetRequest(
    @Schema(example = "user@example.com")
        @NotBlank(message = "Email is required") @Email(message = "Email must be a valid email address") String email) {}
