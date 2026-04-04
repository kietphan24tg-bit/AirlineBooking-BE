package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest", description = "Registration payload for creating a new user account")
public record RegisterRequest(
    @Schema(example = "user@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    String email,
    @Schema(example = "secret123")
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must have at least 6 characters")
    String password,
    @Schema(example = "Jane")
    @NotBlank(message = "First name is required")
    String firstName,
    @Schema(example = "Doe")
    @NotBlank(message = "Last name is required")
    String lastName,
    @Schema(example = "true")
    @NotNull(message = "Accept terms is required")
    Boolean acceptTerms,
    @Schema(example = "+84901234567", nullable = true)
    String phone,
    @Schema(example = "VN", nullable = true)
    String nationality) {}
