package com.example.moduleflight.repository;

import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FareClassPriceEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareClassPriceRepository extends JpaRepository<FareClassPriceEntity, Long> {

  Optional<FareClassPriceEntity> findByFareClassAndCurrencyAndCountryCode(
      FareClassEntity fareClass, String currency, String countryCode);
}
