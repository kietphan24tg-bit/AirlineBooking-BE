package com.example.moduleflight.repository;

import com.example.moduleflight.entity.MarketBasePriceEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketBasePriceRepository extends JpaRepository<MarketBasePriceEntity, Long> {

  Optional<MarketBasePriceEntity> findByOriginCountryCodeAndDestinationCountryCodeAndCurrency(
      String originCountryCode, String destinationCountryCode, String currency);
}
