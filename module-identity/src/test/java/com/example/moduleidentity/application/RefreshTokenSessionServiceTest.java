package com.example.moduleidentity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.AppException;
import com.example.moduleidentity.domain.security.TokenHashingService;
import com.example.moduleidentity.entity.RefreshTokenSessionEntity;
import com.example.moduleidentity.entity.UserEntity;
import com.example.moduleidentity.repository.RefreshTokenSessionRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenSessionServiceTest {

  @Mock private RefreshTokenSessionRepository refreshTokenSessionRepository;

  private final TokenHashingService tokenHashingService = new TokenHashingService();
  private RefreshTokenSessionService refreshTokenSessionService;

  @BeforeEach
  void setUp() {
    refreshTokenSessionService =
        new RefreshTokenSessionService(refreshTokenSessionRepository, tokenHashingService);
  }

  @Test
  void shouldReturnActiveSessionByRawRefreshToken() {
    RefreshTokenSessionEntity session = session(false, OffsetDateTime.now().plusMinutes(5));
    String rawToken = "active-refresh-token";
    when(refreshTokenSessionRepository.findByRefreshTokenHash(tokenHashingService.hashToken(rawToken)))
        .thenReturn(Optional.of(session));

    RefreshTokenSessionEntity result = refreshTokenSessionService.requireActiveSession(rawToken);

    assertSame(session, result);
  }

  @Test
  void shouldRejectRevokedSession() {
    RefreshTokenSessionEntity session = session(true, OffsetDateTime.now().plusMinutes(5));
    String rawToken = "revoked-refresh-token";
    when(refreshTokenSessionRepository.findByRefreshTokenHash(tokenHashingService.hashToken(rawToken)))
        .thenReturn(Optional.of(session));

    AppException exception =
        assertThrows(
            AppException.class, () -> refreshTokenSessionService.requireActiveSession(rawToken));

    assertEquals("REFRESH_TOKEN_REVOKED", exception.getCode());
  }

  @Test
  void shouldRejectExpiredSession() {
    RefreshTokenSessionEntity session = session(false, OffsetDateTime.now().minusMinutes(1));
    String rawToken = "expired-refresh-token";
    when(refreshTokenSessionRepository.findByRefreshTokenHash(tokenHashingService.hashToken(rawToken)))
        .thenReturn(Optional.of(session));

    AppException exception =
        assertThrows(
            AppException.class, () -> refreshTokenSessionService.requireActiveSession(rawToken));

    assertEquals("REFRESH_TOKEN_EXPIRED", exception.getCode());
  }

  @Test
  void shouldMigrateLegacyRawTokenLookupToHashedToken() {
    RefreshTokenSessionEntity session = session(false, OffsetDateTime.now().plusMinutes(5));
    session.setRefreshTokenHash("legacy-raw-token");

    when(refreshTokenSessionRepository.findByRefreshTokenHash(tokenHashingService.hashToken("legacy-raw-token")))
        .thenReturn(Optional.empty());
    when(refreshTokenSessionRepository.findByRefreshTokenHash("legacy-raw-token"))
        .thenReturn(Optional.of(session));
    when(refreshTokenSessionRepository.save(session)).thenReturn(session);

    RefreshTokenSessionEntity result =
        refreshTokenSessionService.requireActiveSession("legacy-raw-token");

    assertSame(session, result);
    assertEquals(tokenHashingService.hashToken("legacy-raw-token"), session.getRefreshTokenHash());
    assertNotNull(session.getUpdatedAt());
    verify(refreshTokenSessionRepository).save(session);
  }

  @Test
  void shouldRevokeSessionByRefreshTokenWhenActive() {
    RefreshTokenSessionEntity session = session(false, OffsetDateTime.now().plusMinutes(5));
    String rawToken = "logout-refresh-token";
    when(refreshTokenSessionRepository.findByRefreshTokenHash(tokenHashingService.hashToken(rawToken)))
        .thenReturn(Optional.of(session));
    when(refreshTokenSessionRepository.save(session)).thenReturn(session);

    refreshTokenSessionService.revokeByRefreshToken(rawToken);

    assertEquals(true, session.isRevoked());
    assertNotNull(session.getUpdatedAt());
    verify(refreshTokenSessionRepository).save(session);
  }

  @Test
  void shouldDelegateAtomicRevokeIfActiveToRepository() {
    when(refreshTokenSessionRepository.revokeIfActive(eq(11L), any(OffsetDateTime.class))).thenReturn(1);

    boolean result = refreshTokenSessionService.revokeIfActive(11L);

    assertEquals(true, result);
  }

  @Test
  void shouldDeleteExpiredSessionsUsingCurrentCutoff() {
    when(refreshTokenSessionRepository.deleteByExpiresAtBefore(any(OffsetDateTime.class))).thenReturn(3L);

    long deleted = refreshTokenSessionService.deleteExpiredSessions();

    assertEquals(3L, deleted);
    ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(refreshTokenSessionRepository).deleteByExpiresAtBefore(cutoffCaptor.capture());
    assertNotNull(cutoffCaptor.getValue());
  }

  private static RefreshTokenSessionEntity session(boolean revoked, OffsetDateTime expiresAt) {
    RefreshTokenSessionEntity session = new RefreshTokenSessionEntity();
    session.setId(1L);
    session.setUser(new UserEntity());
    session.setRevoked(revoked);
    session.setExpiresAt(expiresAt);
    session.setCreatedAt(OffsetDateTime.now());
    session.setUpdatedAt(OffsetDateTime.now());
    session.setRefreshTokenHash("placeholder");
    return session;
  }
}
