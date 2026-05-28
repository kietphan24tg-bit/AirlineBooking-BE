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
@Table(name = "flight_change_settlement_items")
@Getter
@Setter
@NoArgsConstructor
public class FlightChangeSettlementItemEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "settlement_id", nullable = false)
  private FlightChangeSettlementEntity settlement;

  @Column(name = "ticket_id", nullable = false)
  private Long ticketId;

  @Column(name = "passenger_id", nullable = false)
  private Long passengerId;

  @Column(name = "passenger_name", length = 255)
  private String passengerName;

  @Column(name = "passenger_type", length = 20)
  private String passengerType;

  @Column(name = "original_fare_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal originalFareAmount;

  @Column(name = "new_fare_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal newFareAmount;

  @Column(name = "fare_difference", nullable = false, precision = 18, scale = 2)
  private BigDecimal fareDifference;

  @Column(name = "change_fee", nullable = false, precision = 18, scale = 2)
  private BigDecimal changeFee;

  @Column(name = "tax_difference", nullable = false, precision = 18, scale = 2)
  private BigDecimal taxDifference;

  @Column(name = "net_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal netAmount;
}
