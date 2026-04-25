package com.example.modulebooking.entity;

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
@Table(name = "passenger_seat_assignments")
@Getter
@Setter
@NoArgsConstructor
public class PassengerSeatAssignmentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "booking_id", nullable = false)
  private BookingEntity booking;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "passenger_id", nullable = false)
  private PassengerEntity passenger;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "flight_seat_id", nullable = false)
  private FlightSeatEntity flightSeat;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "surcharge_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal surchargeAmount;

  @Column(length = 10)
  private String currency;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
