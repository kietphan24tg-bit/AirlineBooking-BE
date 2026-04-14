package com.example.moduleflight.repository;

import com.example.moduleflight.entity.AircraftLayoutSeatTypeZoneEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftLayoutSeatTypeZoneRepository
    extends JpaRepository<AircraftLayoutSeatTypeZoneEntity, Long> {

  List<AircraftLayoutSeatTypeZoneEntity> findByAircraftLayoutIdOrderByRowStartAsc(Long aircraftLayoutId);
}
