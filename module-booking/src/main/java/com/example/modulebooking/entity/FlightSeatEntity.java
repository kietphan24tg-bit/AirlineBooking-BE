package com.example.modulebooking.entity;

import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.SeatEntity;
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
@Table(name = "flight_seats")
@Getter
@Setter
@NoArgsConstructor
public class FlightSeatEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "flight_id", nullable = false)
  private FlightEntity flight;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seat_id", nullable = false)
  private SeatEntity seat;

  @Column(nullable = false, length = 20)
  private String status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "fare_class_id")
  private FareClassEntity fareClass;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private BookingEntity booking;

  @Column(name = "reserved_until")
  private OffsetDateTime reservedUntil;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
