package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LogoutRequest", description = "Refresh token to revoke during logout")
public record LogoutRequest(
    @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") @NotBlank(message = "Refresh token is required") String refreshToken) {}
