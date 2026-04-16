package com.example.moduleflight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fare_class_prices")
@Getter
@Setter
@NoArgsConstructor
public class FareClassPriceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fare_class_id", nullable = false)
  private FareClassEntity fareClass;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "country_code", nullable = false, length = 2)
  private String countryCode;

  @Column(name = "base_price", nullable = false, precision = 18, scale = 2)
  private BigDecimal basePrice;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
