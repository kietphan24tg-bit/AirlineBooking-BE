package com.example.moduleidentity.repository;

import com.example.moduleidentity.entity.RefreshTokenSessionEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenSessionRepository
    extends JpaRepository<RefreshTokenSessionEntity, Long> {

  Optional<RefreshTokenSessionEntity> findByRefreshToken(String refreshToken);

  List<RefreshTokenSessionEntity> findAllByUser_IdAndRevokedFalse(Long userId);

  long deleteByExpiresAtBefore(OffsetDateTime cutoff);
}
