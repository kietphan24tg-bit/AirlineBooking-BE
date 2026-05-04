package com.example.modulepayment.entity;

import com.example.modulebooking.entity.BookingEntity;
import com.example.moduleidentity.entity.UserEntity;
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
@Table(name = "booking_cancellations")
@Getter
@Setter
@NoArgsConstructor
public class BookingCancellationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "booking_id", nullable = false)
  private BookingEntity booking;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payment_id", nullable = false)
  private PaymentEntity payment;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "requested_by_user_id", nullable = false)
  private UserEntity requestedByUser;

  @Column(name = "idempotency_key", nullable = false, length = 255)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Column(length = 255)
  private String reason;

  @Column(nullable = false, length = 30)
  private String provider;

  @Column(name = "provider_refund_id", length = 255)
  private String providerRefundId;

  @Column(name = "provider_status", length = 50)
  private String providerStatus;

  @Column(name = "refund_status", nullable = false, length = 30)
  private String refundStatus;

  @Column(name = "error_code", length = 80)
  private String errorCode;

  @Column(name = "error_message", length = 255)
  private String errorMessage;

  @Column(nullable = false, length = 10)
  private String currency;

  @Column(name = "fare_gross_total", nullable = false, precision = 18, scale = 2)
  private BigDecimal fareGrossTotal;

  @Column(name = "promo_allocated_total", nullable = false, precision = 18, scale = 2)
  private BigDecimal promoAllocatedTotal;

  @Column(name = "fare_net_total", nullable = false, precision = 18, scale = 2)
  private BigDecimal fareNetTotal;

  @Column(name = "seat_refund_total", nullable = false, precision = 18, scale = 2)
  private BigDecimal seatRefundTotal;

  @Column(name = "service_refund_total", nullable = false, precision = 18, scale = 2)
  private BigDecimal serviceRefundTotal;

  @Column(name = "refund_fee_total", nullable = false, precision = 18, scale = 2)
  private BigDecimal refundFeeTotal;

  @Column(name = "net_refund_total", nullable = false, precision = 18, scale = 2)
  private BigDecimal netRefundTotal;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "processed_at")
  private OffsetDateTime processedAt;

  @OneToMany(
      mappedBy = "cancellation",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<BookingCancellationItemEntity> items = new ArrayList<>();
}
