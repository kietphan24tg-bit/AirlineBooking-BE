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
@Table(name = "tax_fees")
@Getter
@Setter
@NoArgsConstructor
public class TaxFeeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "amount_type", nullable = false, length = 20)
  private String amountType;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal value;

  @Column(name = "apply_to", nullable = false, length = 20)
  private String applyTo;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "is_active", nullable = false)
  private boolean isActive;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
