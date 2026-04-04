package com.example.moduleidentity.application;

import com.example.common.exception.AppException;
import com.example.moduleidentity.domain.security.TokenHashingService;
import com.example.moduleidentity.entity.RefreshTokenSessionEntity;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.repository.RefreshTokenSessionRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenSessionService {

  private final RefreshTokenSessionRepository refreshTokenSessionRepository;
  private final TokenHashingService tokenHashingService;

  public RefreshTokenSessionService(
      RefreshTokenSessionRepository refreshTokenSessionRepository,
      TokenHashingService tokenHashingService) {
    this.refreshTokenSessionRepository = refreshTokenSessionRepository;
    this.tokenHashingService = tokenHashingService;
  }

  @Transactional
  public RefreshTokenSessionEntity createSession(
      UserEntity user, String refreshToken, OffsetDateTime expiresAt) {
    RefreshTokenSessionEntity session = new RefreshTokenSessionEntity();
    OffsetDateTime now = OffsetDateTime.now();
    session.setUser(user);
    session.setRefreshTokenHash(tokenHashingService.hashToken(refreshToken));
    session.setRevoked(false);
    session.setExpiresAt(expiresAt);
    session.setCreatedAt(now);
    session.setUpdatedAt(now);
    return refreshTokenSessionRepository.save(session);
  }

  @Transactional(readOnly = true)
  public RefreshTokenSessionEntity requireActiveSession(String refreshToken) {
    RefreshTokenSessionEntity session = requireSessionByRawToken(refreshToken);

    if (session.isRevoked()) {
      throw new AppException("REFRESH_TOKEN_REVOKED", "Refresh token has been revoked", 401);
    }

    if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw new AppException("REFRESH_TOKEN_EXPIRED", "Refresh token has expired", 401);
    }

    return session;
  }

  @Transactional
  public void revokeByRefreshToken(String refreshToken) {
    findSessionByRawToken(refreshToken)
        .filter(session -> !session.isRevoked())
        .ifPresent(
            session -> {
              session.setRevoked(true);
              session.setUpdatedAt(OffsetDateTime.now());
              refreshTokenSessionRepository.save(session);
            });
  }

  @Transactional
  public boolean revokeIfActive(Long sessionId) {
    return refreshTokenSessionRepository.revokeIfActive(sessionId, OffsetDateTime.now()) > 0;
  }

  @Transactional
  public int revokeAllActiveByUserId(Long userId) {
    OffsetDateTime now = OffsetDateTime.now();
    var sessions = refreshTokenSessionRepository.findAllByUser_IdAndRevokedFalse(userId);
    sessions.forEach(
        session -> {
          session.setRevoked(true);
          session.setUpdatedAt(now);
        });
    refreshTokenSessionRepository.saveAll(sessions);
    return sessions.size();
  }

  @Transactional
  public long deleteExpiredSessions() {
    return refreshTokenSessionRepository.deleteByExpiresAtBefore(OffsetDateTime.now());
  }

  private RefreshTokenSessionEntity requireSessionByRawToken(String rawRefreshToken) {
    return findSessionByRawToken(rawRefreshToken)
        .orElseThrow(
            () ->
                new AppException("REFRESH_TOKEN_NOT_FOUND", "Refresh token session not found", 401));
  }

  private java.util.Optional<RefreshTokenSessionEntity> findSessionByRawToken(
      String rawRefreshToken) {
    String hashed = tokenHashingService.hashToken(rawRefreshToken);
    var byHashed = refreshTokenSessionRepository.findByRefreshTokenHash(hashed);
    if (byHashed.isPresent()) {
      return byHashed;
    }

    // Backward compatibility for legacy rows that may still contain raw token value.
    var byLegacyRaw = refreshTokenSessionRepository.findByRefreshTokenHash(rawRefreshToken);
    byLegacyRaw.ifPresent(
        session -> {
          session.setRefreshTokenHash(hashed);
          session.setUpdatedAt(OffsetDateTime.now());
          refreshTokenSessionRepository.save(session);
        });
    return byLegacyRaw;
  }
}
