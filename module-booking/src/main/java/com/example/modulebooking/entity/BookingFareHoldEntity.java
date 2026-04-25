package com.example.modulebooking.entity;

import com.example.moduleflight.entity.FlightFareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "booking_fare_holds")
@Getter
@Setter
@NoArgsConstructor
public class BookingFareHoldEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "booking_id", nullable = false)
  private BookingEntity booking;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "flight_fare_id", nullable = false)
  private FlightFareEntity flightFare;

  @Column(nullable = false)
  private int quantity;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
