package com.example.moduleflight.repository;

import com.example.moduleflight.entity.AircraftLayoutEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftLayoutRepository extends JpaRepository<AircraftLayoutEntity, Long> {

  Optional<AircraftLayoutEntity> findByAircraftId(Long aircraftId);
}
