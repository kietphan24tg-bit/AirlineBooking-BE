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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class PaymentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "booking_id", nullable = false)
  private BookingEntity booking;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 10)
  private String currency;

  @Column(name = "payment_method", nullable = false, length = 30)
  private String paymentMethod;

  @Column(name = "payment_status", nullable = false, length = 20)
  private String paymentStatus;

  @Column(name = "transaction_id", unique = true, length = 100)
  private String transactionId;

  @Column(name = "qr_data", columnDefinition = "text")
  private String qrData;

  @Column(name = "qr_image_url", length = 500)
  private String qrImageUrl;

  @Column(length = 30)
  private String type;

  @Column(name = "paid_at")
  private OffsetDateTime paidAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
