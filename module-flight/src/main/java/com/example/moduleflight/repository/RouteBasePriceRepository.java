package com.example.moduleflight.repository;

import com.example.moduleflight.entity.RouteBasePriceEntity;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteBasePriceRepository extends JpaRepository<RouteBasePriceEntity, Long> {

  @Query(
      """
      select rbp
      from RouteBasePriceEntity rbp
      where rbp.originAirportCode = :originAirportCode
        and rbp.destinationAirportCode = :destinationAirportCode
        and rbp.currency = :currency
        and (rbp.activeFrom is null or rbp.activeFrom <= :flightDate)
        and (rbp.activeUntil is null or rbp.activeUntil >= :flightDate)
      """)
  Optional<RouteBasePriceEntity> findActiveByRouteAndCurrency(
      @Param("originAirportCode") String originAirportCode,
      @Param("destinationAirportCode") String destinationAirportCode,
      @Param("currency") String currency,
      @Param("flightDate") LocalDate flightDate);
}
