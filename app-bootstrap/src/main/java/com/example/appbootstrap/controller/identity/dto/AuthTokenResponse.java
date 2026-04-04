package com.example.appbootstrap.controller.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "AuthTokenResponse", description = "Authentication tokens returned after login or refresh")
public record AuthTokenResponse(
    @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
    @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String refreshToken,
    @Schema(example = "Bearer") String tokenType,
    @Schema(example = "2026-04-04T10:30:30Z") OffsetDateTime expiresAt) {}
