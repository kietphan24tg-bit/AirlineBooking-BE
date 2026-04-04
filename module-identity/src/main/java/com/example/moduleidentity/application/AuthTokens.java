package com.example.moduleidentity.application;

import java.time.OffsetDateTime;

public record AuthTokens(
    String accessToken, String refreshToken, String tokenType, OffsetDateTime expiresAt) {}
