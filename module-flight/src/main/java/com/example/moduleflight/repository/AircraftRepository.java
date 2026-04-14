package com.example.moduleflight.repository;

import com.example.moduleflight.entity.AircraftEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftRepository extends JpaRepository<AircraftEntity, Long> {

  Optional<AircraftEntity> findFirstByModelIgnoreCase(String model);
}
