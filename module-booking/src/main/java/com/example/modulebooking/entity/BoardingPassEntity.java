package com.example.modulebooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "boarding_passes")
@Getter
@Setter
@NoArgsConstructor
public class BoardingPassEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", nullable = false, unique = true)
  private TicketEntity ticket;

  @Column(name = "qr_code", nullable = false, columnDefinition = "text")
  private String qrCode;

  @Column(length = 20)
  private String gate;

  @Column(name = "boarding_time")
  private OffsetDateTime boardingTime;

  @Column(name = "sequence_number")
  private Integer sequenceNumber;

  @Column(name = "seat_label", length = 10)
  private String seatLabel;

  @Column(name = "is_boarded", nullable = false)
  private boolean isBoarded;

  @Column(name = "boarded_at")
  private OffsetDateTime boardedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
