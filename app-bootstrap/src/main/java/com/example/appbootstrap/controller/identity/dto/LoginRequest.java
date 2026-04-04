package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Credentials used to authenticate a user")
public record LoginRequest(
    @Schema(example = "user@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    String email,
    @Schema(example = "secret123")
    @NotBlank(message = "Password is required")
    String password) {}
