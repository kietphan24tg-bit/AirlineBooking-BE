package com.example.moduleflight.repository;

import com.example.moduleflight.entity.AircraftLayoutZoneEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftLayoutZoneRepository
    extends JpaRepository<AircraftLayoutZoneEntity, Long> {

  List<AircraftLayoutZoneEntity> findByAircraftLayoutIdOrderByRowStartAsc(Long aircraftLayoutId);
}
