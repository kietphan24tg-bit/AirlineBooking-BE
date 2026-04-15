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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "flights",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_flights_airline_number_date",
            columnNames = {"airline_id", "flight_number", "flight_date"}))
@Getter
@Setter
@NoArgsConstructor
public class FlightEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "airline_id", nullable = false)
  private AirlineEntity airline;

  @Column(name = "flight_number", nullable = false, length = 30)
  private String flightNumber;

  @Column(name = "flight_date", nullable = false)
  private LocalDate flightDate;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "aircraft_id", nullable = false)
  private AircraftEntity aircraft;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "departure_airport_id", nullable = false)
  private AirportEntity departureAirport;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "arrival_airport_id", nullable = false)
  private AirportEntity arrivalAirport;

  @Column(name = "departure_time", nullable = false)
  private OffsetDateTime departureTime;

  @Column(name = "arrival_time", nullable = false)
  private OffsetDateTime arrivalTime;

  @Column(name = "departure_terminal", length = 20)
  private String departureTerminal;

  @Column(name = "departure_gate", length = 20)
  private String departureGate;

  @Column(name = "arrival_terminal", length = 20)
  private String arrivalTerminal;

  @Column(name = "arrival_gate", length = 20)
  private String arrivalGate;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
