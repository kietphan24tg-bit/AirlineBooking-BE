package com.example.moduleflight.repository;

import com.example.moduleflight.entity.AircraftIcaoMappingEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftIcaoMappingRepository
    extends JpaRepository<AircraftIcaoMappingEntity, Long> {

  Optional<AircraftIcaoMappingEntity> findByIcaoCode(String icaoCode);
}
