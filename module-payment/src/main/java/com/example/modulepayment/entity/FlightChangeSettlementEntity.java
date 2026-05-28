package com.example.modulepayment.entity;

import com.example.modulebooking.entity.BookingEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "flight_change_settlements")
@Getter
@Setter
@NoArgsConstructor
public class FlightChangeSettlementEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "booking_id", nullable = false)
  private BookingEntity booking;

  @Column(name = "quote_token", length = 120)
  private String quoteToken;

  @Column(name = "idempotency_key", length = 120)
  private String idempotencyKey;

  @Column(name = "current_flight_id", nullable = false)
  private Long currentFlightId;

  @Column(name = "new_flight_id", nullable = false)
  private Long newFlightId;

  @Column(name = "new_fare_id", nullable = false)
  private Long newFareId;

  @Column(name = "settlement_type", nullable = false, length = 30)
  private String settlementType;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal additionalAmount;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal refundAmount;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal netAmount;

  @Column(nullable = false, length = 10)
  private String currency;

  @Column(name = "provider", length = 30)
  private String provider;

  @Column(name = "provider_refund_id", length = 255)
  private String providerRefundId;

  @Column(name = "provider_payment_reference", length = 255)
  private String providerPaymentReference;

  @Column(name = "error_code", length = 80)
  private String errorCode;

  @Column(name = "error_message", length = 500)
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<FlightChangeSettlementItemEntity> items = new ArrayList<>();
}
