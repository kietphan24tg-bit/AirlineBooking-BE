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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "promotion_values")
@Getter
@Setter
@NoArgsConstructor
public class PromotionValueSnapshotEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "promotion_id", nullable = false)
  private PromotionSnapshotEntity promotion;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "country_code", nullable = false, length = 2)
  private String countryCode;

  @Column(name = "fixed_discount_value", precision = 18, scale = 2)
  private BigDecimal fixedDiscountValue;

  @Column(name = "max_discount_value", precision = 18, scale = 2)
  private BigDecimal maxDiscountValue;
}
