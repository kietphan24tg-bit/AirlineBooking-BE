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
@Table(name = "fare_classes")
@Getter
@Setter
@NoArgsConstructor
public class FareClassEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 30)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "cabin_class", nullable = false, length = 30)
  private String cabinClass;

  @Column(name = "baggage_checkin_kg", nullable = false)
  private Integer baggageCheckinKg;

  @Column(name = "baggage_hand_kg", nullable = false)
  private Integer baggageHandKg;

  @Column(name = "change_fee", nullable = false, precision = 18, scale = 2)
  private BigDecimal changeFee;

  @Column(name = "refund_fee", nullable = false, precision = 18, scale = 2)
  private BigDecimal refundFee;

  @Column(name = "is_refundable", nullable = false)
  private boolean isRefundable;

  @Column(name = "is_changeable", nullable = false)
  private boolean isChangeable;

  @Column(name = "is_priority_checkin", nullable = false)
  private boolean isPriorityCheckin;

  @Column(name = "is_priority_boarding", nullable = false)
  private boolean isPriorityBoarding;

  @Column(name = "mileage_rate", nullable = false, precision = 8, scale = 2)
  private BigDecimal mileageRate;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
