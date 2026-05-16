package com.example.moduleflight.repository;

import com.example.moduleflight.entity.PricingMarketProfileEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingMarketProfileRepository
    extends JpaRepository<PricingMarketProfileEntity, String> {

  Optional<PricingMarketProfileEntity> findByCountryCode(String countryCode);
}
