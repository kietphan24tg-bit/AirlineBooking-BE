package com.example.moduleflight.entity;

import com.example.moduleflight.application.AirportUsageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "user_recent_airports",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_user_recent_airports_user_airport_usage",
            columnNames = {"user_id", "airport_id", "usage_type"}),
    indexes =
        @Index(
            name = "idx_user_recent_airports_user_last_used",
            columnList = "user_id, last_used_at desc"))
@Getter
@Setter
@NoArgsConstructor
public class UserRecentAirportEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "airport_id", nullable = false)
  private AirportEntity airport;

  @Enumerated(EnumType.STRING)
  @Column(name = "usage_type", nullable = false, length = 30)
  private AirportUsageType usageType;

  @Column(name = "last_used_at", nullable = false)
  private OffsetDateTime lastUsedAt;

  @Column(name = "search_count", nullable = false)
  private int searchCount;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
