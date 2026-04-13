package com.example.modulepayment.entity;

import com.example.modulebooking.entity.BookingEntity;
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
@Table(name = "payment_webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class PaymentWebhookEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 30)
  private String provider;

  @Column(name = "idempotency_key", nullable = false, length = 255)
  private String idempotencyKey;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_id")
  private PaymentEntity payment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private BookingEntity booking;

  @Column(name = "transaction_id", length = 100)
  private String transactionId;

  @Column(name = "order_code")
  private Long orderCode;

  @Column(name = "event_type", nullable = false, length = 40)
  private String eventType;

  @Column(name = "processing_status", nullable = false, length = 30)
  private String processingStatus;

  @Column(name = "is_final", nullable = false)
  private boolean isFinal;

  @Column(length = 255)
  private String signature;

  @Column(name = "signature_valid", nullable = false)
  private boolean signatureValid;

  @Column(name = "raw_payload", nullable = false, columnDefinition = "text")
  private String rawPayload;

  @Column(name = "error_code", length = 80)
  private String errorCode;

  @Column(name = "error_message", length = 255)
  private String errorMessage;

  @Column(name = "received_at", nullable = false)
  private OffsetDateTime receivedAt;

  @Column(name = "processed_at")
  private OffsetDateTime processedAt;
}
