package com.example.modulebooking.repository;

import com.example.modulebooking.entity.PromotionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<PromotionEntity, Long> {

  Optional<PromotionEntity> findByCodeIgnoreCase(String code);
}
