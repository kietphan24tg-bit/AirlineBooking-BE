package com.example.appbootstrap.security;

import com.example.appbootstrap.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey signingKey;
  private final long accessTokenMinutes;
  private final long refreshTokenDays;

  public JwtService(JwtProperties jwtProperties) {
    this.signingKey = Keys.hmacShaKeyFor(resolveSecretBytes(jwtProperties.secret()));
    this.accessTokenMinutes = jwtProperties.accessTokenExpirationMinutes();
    this.refreshTokenDays = jwtProperties.refreshTokenExpirationDays();
  }

  // ── Token Generation ──────────────────────────────────────────────

  public String generateAccessToken(String email, Long userId) {
    return buildToken(
        Map.of("userId", userId, "type", "access"),
        email,
        Instant.now().plus(accessTokenMinutes, ChronoUnit.MINUTES));
  }

  public String generateRefreshToken(String email, Long userId) {
    return buildToken(
        Map.of("userId", userId, "type", "refresh"),
        email,
        Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS));
  }

  // ── Token Validation ──────────────────────────────────────────────

  public boolean isTokenValid(String token, String expectedEmail) {
    try {
      String email = extractEmail(token);
      return email.equals(expectedEmail) && !isTokenExpired(token);
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  public boolean isAccessToken(String token) {
    return "access".equals(extractClaim(token, c -> c.get("type", String.class)));
  }

  // ── Claim Extraction ──────────────────────────────────────────────

  public String extractEmail(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public Long extractUserId(String token) {
    return extractClaim(token, c -> c.get("userId", Long.class));
  }

  public <T> T extractClaim(String token, Function<Claims, T> resolver) {
    return resolver.apply(extractAllClaims(token));
  }

  // ── Private Helpers ───────────────────────────────────────────────

  private String buildToken(Map<String, Object> extraClaims, String subject, Instant expiry) {
    return Jwts.builder()
        .claims(extraClaims)
        .subject(subject)
        .issuedAt(Date.from(Instant.now()))
        .expiration(Date.from(expiry))
        .signWith(signingKey)
        .compact();
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
  }

  private boolean isTokenExpired(String token) {
    try {
      return extractClaim(token, Claims::getExpiration).before(Date.from(Instant.now()));
    } catch (ExpiredJwtException e) {
      return true;
    }
  }

  private byte[] resolveSecretBytes(String secret) {
    try {
      return Decoders.BASE64.decode(secret);
    } catch (DecodingException ignored) {
      return secret.getBytes(StandardCharsets.UTF_8);
    }
  }
}
