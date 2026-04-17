package com.example.moduleidentity.repository;

import com.example.moduleidentity.entity.PasswordResetTokenEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetTokenRepository
    extends JpaRepository<PasswordResetTokenEntity, Long> {

  Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

  boolean existsByTokenHash(String tokenHash);

  void deleteByUser_Id(Long userId);

  long deleteByExpiresAtBefore(OffsetDateTime cutoff);
}
