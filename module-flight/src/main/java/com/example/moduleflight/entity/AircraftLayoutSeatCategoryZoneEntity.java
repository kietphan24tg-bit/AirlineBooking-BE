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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "aircraft_layout_seat_category_zones")
@Getter
@Setter
@NoArgsConstructor
public class AircraftLayoutSeatCategoryZoneEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "aircraft_layout_id", nullable = false)
  private AircraftLayoutEntity aircraftLayout;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seat_category_id", nullable = false)
  private SeatCategoryEntity seatCategory;

  @Column(name = "row_start", nullable = false)
  @Min(1) private Integer rowStart;

  @Column(name = "row_end", nullable = false)
  @Min(1) private Integer rowEnd;

  @Column(name = "columns", length = 20)
  private String columns;

  @AssertTrue(message = "rowStart and rowEnd must be > 0 and rowStart <= rowEnd") private boolean isRowRangeValid() {
    return rowStart != null && rowEnd != null && rowStart <= rowEnd;
  }
}
