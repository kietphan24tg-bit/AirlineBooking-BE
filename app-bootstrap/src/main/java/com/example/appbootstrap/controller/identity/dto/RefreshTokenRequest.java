package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshTokenRequest", description = "Refresh token used to obtain a new access token")
public record RefreshTokenRequest(
    @Schema(example = "eyJhbGciOiJIUzI1NiJ9...")
    @NotBlank(message = "Refresh token is required")
    String refreshToken) {}
