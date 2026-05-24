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
@Table(name = "seat_change_settlement_items")
@Getter
@Setter
@NoArgsConstructor
public class SeatChangeSettlementItemEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "settlement_id", nullable = false)
  private SeatChangeSettlementEntity settlement;

  @Column(name = "passenger_id", nullable = false)
  private Long passengerId;

  @Column(name = "flight_id", nullable = false)
  private Long flightId;

  @Column(name = "from_seat_id")
  private Long fromSeatId;

  @Column(name = "from_seat_number", length = 10)
  private String fromSeatNumber;

  @Column(name = "to_seat_id")
  private Long toSeatId;

  @Column(name = "to_seat_number", length = 10)
  private String toSeatNumber;

  @Column(name = "old_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal oldAmount;

  @Column(name = "new_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal newAmount;

  @Column(name = "delta_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal deltaAmount;
}
