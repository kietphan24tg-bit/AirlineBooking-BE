package com.example.moduleidentity.repository;

import com.example.moduleidentity.entity.RefreshTokenSessionEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenSessionRepository
    extends JpaRepository<RefreshTokenSessionEntity, Long> {

  Optional<RefreshTokenSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

  List<RefreshTokenSessionEntity> findAllByUser_IdAndRevokedFalse(Long userId);

  long deleteByExpiresAtBefore(OffsetDateTime cutoff);

  @Modifying
  @Query(
      """
      update RefreshTokenSessionEntity session
         set session.revoked = true,
             session.updatedAt = :updatedAt
       where session.id = :sessionId
         and session.revoked = false
      """)
  int revokeIfActive(@Param("sessionId") Long sessionId, @Param("updatedAt") OffsetDateTime updatedAt);
}
