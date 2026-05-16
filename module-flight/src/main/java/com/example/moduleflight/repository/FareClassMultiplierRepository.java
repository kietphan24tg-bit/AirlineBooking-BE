package com.example.moduleflight.repository;

import com.example.moduleflight.entity.FareClassMultiplierEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareClassMultiplierRepository
    extends JpaRepository<FareClassMultiplierEntity, Long> {

  Optional<FareClassMultiplierEntity> findByFareCodeAndTripScope(String fareCode, String tripScope);
}
