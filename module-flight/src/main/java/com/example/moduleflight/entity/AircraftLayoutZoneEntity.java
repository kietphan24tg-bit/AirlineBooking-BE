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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "aircraft_layout_zones")
@Getter
@Setter
@NoArgsConstructor
public class AircraftLayoutZoneEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "aircraft_layout_id", nullable = false)
  private AircraftLayoutEntity aircraftLayout;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seat_class_id", nullable = false)
  private SeatClassEntity seatClass;

  @Column(name = "row_start", nullable = false)
  private Integer rowStart;

  @Column(name = "row_end", nullable = false)
  private Integer rowEnd;

  @Column(name = "columns", length = 20)
  private String columns;
}
