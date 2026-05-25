package com.example.modulepayment.entity;

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
@Table(name = "service_change_settlement_items")
@Getter
@Setter
@NoArgsConstructor
public class ServiceChangeSettlementItemEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "settlement_id", nullable = false)
  private ServiceChangeSettlementEntity settlement;

  @Column(name = "passenger_id", nullable = false)
  private Long passengerId;

  @Column(name = "service_id", nullable = false)
  private Long serviceId;

  @Column(name = "option_id", nullable = false)
  private Long optionId;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "service_type", length = 30)
  private String serviceType;

  @Column(name = "service_name", length = 255)
  private String serviceName;

  @Column(name = "option_name", length = 255)
  private String optionName;

  @Column(name = "old_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal oldAmount;

  @Column(name = "new_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal newAmount;

  @Column(name = "delta_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal deltaAmount;
}
