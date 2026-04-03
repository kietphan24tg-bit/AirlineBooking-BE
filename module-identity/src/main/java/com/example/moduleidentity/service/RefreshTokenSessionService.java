package com.example.moduleidentity.service;

import com.example.common.exception.AppException;
import com.example.moduleidentity.entity.RefreshTokenSessionEntity;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.repository.RefreshTokenSessionRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenSessionService {

  private final RefreshTokenSessionRepository refreshTokenSessionRepository;

  public RefreshTokenSessionService(RefreshTokenSessionRepository refreshTokenSessionRepository) {
    this.refreshTokenSessionRepository = refreshTokenSessionRepository;
  }

  @Transactional
  public RefreshTokenSessionEntity createSession(
      UserEntity user, String refreshToken, OffsetDateTime expiresAt) {
    RefreshTokenSessionEntity session = new RefreshTokenSessionEntity();
    OffsetDateTime now = OffsetDateTime.now();
    session.setUser(user);
    session.setRefreshToken(refreshToken);
    session.setRevoked(false);
    session.setExpiresAt(expiresAt);
    session.setCreatedAt(now);
    session.setUpdatedAt(now);
    return refreshTokenSessionRepository.save(session);
  }

  @Transactional(readOnly = true)
  public RefreshTokenSessionEntity requireActiveSession(String refreshToken) {
    RefreshTokenSessionEntity session =
        refreshTokenSessionRepository
            .findByRefreshToken(refreshToken)
            .orElseThrow(
                () ->
                    new AppException(
                        "REFRESH_TOKEN_NOT_FOUND", "Refresh token session not found", 401));

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
    refreshTokenSessionRepository
        .findByRefreshToken(refreshToken)
        .filter(session -> !session.isRevoked())
        .ifPresent(
            session -> {
              session.setRevoked(true);
              session.setUpdatedAt(OffsetDateTime.now());
              refreshTokenSessionRepository.save(session);
            });
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
}
