package com.example.modulebooking.repository;

import com.example.modulebooking.entity.PromotionEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<PromotionEntity, Long> {

  Optional<PromotionEntity> findByCodeIgnoreCase(String code);

  @Query(
      """
      select p
      from PromotionEntity p
      where p.isActive = true
        and (p.startsAt is null or p.startsAt <= :now)
        and (p.expiresAt is null or p.expiresAt >= :now)
        and (p.usageLimit is null or p.usedCount < p.usageLimit)
      order by p.expiresAt asc nulls last, p.code asc
      """)
  List<PromotionEntity> findDisplayableActivePromotions(@Param("now") OffsetDateTime now);
}
