package com.example.moduleflight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pricing_market_profiles")
@Getter
@Setter
@NoArgsConstructor
public class PricingMarketProfileEntity {

  @Id
  @Column(name = "country_code", nullable = false, length = 2)
  private String countryCode;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "allow_first", nullable = false)
  private boolean allowFirst;

  @Column(name = "tax_profile_code", nullable = false, length = 50)
  private String taxProfileCode;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
