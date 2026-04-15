package com.example.moduleflight.repository;

import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightFareRepository extends JpaRepository<FlightFareEntity, Long> {

  Optional<FlightFareEntity> findByFlightAndFareClass(
      FlightEntity flight, FareClassEntity fareClass);
}
