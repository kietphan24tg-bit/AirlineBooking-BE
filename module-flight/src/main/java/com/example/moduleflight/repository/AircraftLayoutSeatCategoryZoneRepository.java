package com.example.moduleflight.repository;

import com.example.moduleflight.entity.AircraftLayoutSeatCategoryZoneEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftLayoutSeatCategoryZoneRepository
    extends JpaRepository<AircraftLayoutSeatCategoryZoneEntity, Long> {

  List<AircraftLayoutSeatCategoryZoneEntity> findByAircraftLayoutIdOrderByRowStartAsc(
      Long aircraftLayoutId);
}
