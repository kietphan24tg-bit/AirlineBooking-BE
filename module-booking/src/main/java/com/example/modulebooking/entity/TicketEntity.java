package com.example.modulebooking.entity;

import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FlightEntity;
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
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class TicketEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "booking_id", nullable = false)
  private BookingEntity booking;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "flight_fare_id", nullable = false)
  private FlightFareEntity flightFare;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "passenger_id", nullable = false)
  private PassengerEntity passenger;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "flight_id", nullable = false)
  private FlightEntity flight;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "flight_seat_id")
  private FlightSeatEntity flightSeat;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fare_class_id", nullable = false)
  private FareClassEntity fareClass;

  @Column(name = "ticket_number", nullable = false, unique = true, length = 50)
  private String ticketNumber;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(name = "issued_at")
  private OffsetDateTime issuedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
