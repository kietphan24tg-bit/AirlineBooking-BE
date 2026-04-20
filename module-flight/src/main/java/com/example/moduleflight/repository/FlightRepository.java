package com.example.moduleflight.repository;

import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.FlightEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightRepository extends JpaRepository<FlightEntity, Long> {

  Optional<FlightEntity> findByAirlineAndFlightNumberAndFlightDate(
      AirlineEntity airline, String flightNumber, LocalDate flightDate);

  java.util.List<FlightEntity> findByAirlineInAndFlightDateBetween(
      Collection<AirlineEntity> airlines, LocalDate startDate, LocalDate endDate);

  boolean existsByDepartureAirport_IdAndArrivalAirport_IdAndFlightDate(
      Long departureAirportId, Long arrivalAirportId, LocalDate flightDate);
}
