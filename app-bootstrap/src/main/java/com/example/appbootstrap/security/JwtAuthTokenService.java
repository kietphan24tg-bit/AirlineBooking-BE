package com.example.appbootstrap.security;

import com.example.moduleidentity.port.AuthTokenService;
import io.jsonwebtoken.Claims;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class JwtAuthTokenService implements AuthTokenService {

  private final JwtService jwtService;

  public JwtAuthTokenService(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  public String generateAccessToken(String email, Long userId) {
    return jwtService.generateAccessToken(email, userId);
  }

  @Override
  public String generateRefreshToken(String email, Long userId) {
    return jwtService.generateRefreshToken(email, userId);
  }

  @Override
  public boolean isTokenValid(String token, String expectedEmail) {
    return jwtService.isTokenValid(token, expectedEmail);
  }

  @Override
  public boolean isRefreshToken(String token) {
    return jwtService.isRefreshToken(token);
  }

  @Override
  public String extractEmail(String token) {
    return jwtService.extractEmail(token);
  }

  @Override
  public Long extractUserId(String token) {
    return jwtService.extractUserId(token);
  }

  @Override
  public OffsetDateTime extractExpiration(String token) {
    return jwtService
        .extractClaim(token, Claims::getExpiration)
        .toInstant()
        .atOffset(ZoneOffset.UTC);
  }

  @Override
  public OffsetDateTime extractIssuedAt(String token) {
    return jwtService.extractIssuedAt(token);
  }
}
