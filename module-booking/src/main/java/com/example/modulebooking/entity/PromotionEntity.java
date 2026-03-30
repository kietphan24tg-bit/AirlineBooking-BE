package com.example.modulebooking.entity;

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
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
public class PromotionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "discount_type", nullable = false, length = 20)
  private String discountType;

  @Column(name = "discount_value", nullable = false, precision = 18, scale = 2)
  private BigDecimal discountValue;

  @Column(name = "max_discount", precision = 18, scale = 2)
  private BigDecimal maxDiscount;

  @Column(name = "min_booking_amount", precision = 18, scale = 2)
  private BigDecimal minBookingAmount;

  @Column(name = "usage_limit")
  private Integer usageLimit;

  @Column(name = "used_count", nullable = false)
  private Integer usedCount;

  @Column(name = "is_active", nullable = false)
  private boolean isActive;

  @Column(name = "starts_at")
  private OffsetDateTime startsAt;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
