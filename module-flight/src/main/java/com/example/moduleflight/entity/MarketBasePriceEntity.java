package com.example.moduleflight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "market_base_prices")
@Getter
@Setter
@NoArgsConstructor
public class MarketBasePriceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "origin_country_code", nullable = false, length = 2)
  private String originCountryCode;

  @Column(name = "destination_country_code", nullable = false, length = 2)
  private String destinationCountryCode;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "base_economy_price", nullable = false, precision = 18, scale = 2)
  private BigDecimal baseEconomyPrice;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
