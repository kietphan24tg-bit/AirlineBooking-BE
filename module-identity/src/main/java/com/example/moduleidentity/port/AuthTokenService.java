package com.example.moduleidentity.port;

import java.time.OffsetDateTime;

public interface AuthTokenService {

  String generateAccessToken(String email, Long userId);

  String generateRefreshToken(String email, Long userId);

  boolean isTokenValid(String token, String expectedEmail);

  boolean isRefreshToken(String token);

  String extractEmail(String token);

  Long extractUserId(String token);

  OffsetDateTime extractExpiration(String token);

  OffsetDateTime extractIssuedAt(String token);
}
