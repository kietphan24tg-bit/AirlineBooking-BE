package com.example.moduleflight.repository;

import com.example.moduleflight.entity.PromotionSnapshotEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionSnapshotRepository extends JpaRepository<PromotionSnapshotEntity, Long> {

  Optional<PromotionSnapshotEntity> findByCodeIgnoreCase(String code);
}
