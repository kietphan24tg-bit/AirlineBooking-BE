package com.example.moduleflight.repository;

import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightFareRepository extends JpaRepository<FlightFareEntity, Long> {

  Optional<FlightFareEntity> findByFlightAndFareClass(
      FlightEntity flight, FareClassEntity fareClass);

  @Query(
      """
      select ff
      from FlightFareEntity ff
      join fetch ff.flight f
      join fetch f.airline a
      join fetch f.departureAirport dep
      join fetch f.arrivalAirport arr
      join fetch ff.fareClass fc
      left join fetch fc.includedSeatCategory isc
      where dep.code = :fromCode
        and arr.code = :toCode
        and f.flightDate = :flightDate
        and f.status in :flightStatuses
      """)
  List<FlightFareEntity> searchFaresByRouteAndDate(
      @Param("fromCode") String fromCode,
      @Param("toCode") String toCode,
      @Param("flightDate") LocalDate flightDate,
      @Param("flightStatuses") List<String> flightStatuses);

  @Query(
      """
      select ff
      from FlightFareEntity ff
      join fetch ff.flight f
      join fetch f.departureAirport dep
      join fetch ff.fareClass fc
      left join fetch fc.includedSeatCategory isc
      where ff.id = :id
      """)
  Optional<FlightFareEntity> findByIdWithDetails(@Param("id") Long id);
}
