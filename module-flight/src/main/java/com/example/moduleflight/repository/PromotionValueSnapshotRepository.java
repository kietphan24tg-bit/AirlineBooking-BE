package com.example.moduleflight.repository;

import com.example.moduleflight.entity.PromotionSnapshotEntity;
import com.example.moduleflight.entity.PromotionValueSnapshotEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionValueSnapshotRepository
    extends JpaRepository<PromotionValueSnapshotEntity, Long> {

  Optional<PromotionValueSnapshotEntity>
      findFirstByPromotionAndCurrencyIgnoreCaseAndCountryCodeIgnoreCase(
          PromotionSnapshotEntity promotion, String currency, String countryCode);

  List<PromotionValueSnapshotEntity> findAllByPromotion(PromotionSnapshotEntity promotion);
}
