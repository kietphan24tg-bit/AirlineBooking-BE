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
@Table(name = "flight_fares")
@Getter
@Setter
@NoArgsConstructor
public class FlightFareEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "flight_id", nullable = false)
  private FlightEntity flight;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fare_class_id", nullable = false)
  private FareClassEntity fareClass;

  @Column(name = "base_price", nullable = false, precision = 18, scale = 2)
  private BigDecimal basePrice;

  @Column(name = "tax_breakdown", columnDefinition = "jsonb")
  private String taxBreakdown;

  @Column(name = "total_tax_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal totalTaxAmount;

  @Column(name = "total_price", nullable = false, precision = 18, scale = 2)
  private BigDecimal totalPrice;

  @Column(name = "available_seats", nullable = false)
  private Integer availableSeats;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
