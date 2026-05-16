package com.example.moduleflight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "route_base_prices")
@Getter
@Setter
@NoArgsConstructor
public class RouteBasePriceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "origin_airport_code", nullable = false, length = 3)
  private String originAirportCode;

  @Column(name = "destination_airport_code", nullable = false, length = 3)
  private String destinationAirportCode;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "base_economy_price", nullable = false, precision = 18, scale = 2)
  private BigDecimal baseEconomyPrice;

  @Column(name = "active_from")
  private LocalDate activeFrom;

  @Column(name = "active_until")
  private LocalDate activeUntil;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
