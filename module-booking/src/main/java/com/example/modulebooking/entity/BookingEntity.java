package com.example.modulebooking.entity;

import com.example.moduleidentity.entity.UserEntity;

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
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class BookingEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "pnr_code", nullable = false, unique = true, length = 20)
  private String pnrCode;

  @Column(name = "contact_email", nullable = false, length = 255)
  private String contactEmail;

  @Column(name = "contact_phone", nullable = false, length = 30)
  private String contactPhone;

  @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal totalAmount;

  @Column(nullable = false, length = 10)
  private String currency;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(name = "pnr_expires_at")
  private OffsetDateTime pnrExpiresAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "promotion_id")
  private PromotionEntity promotion;

  @Column(name = "discount_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal discountAmount;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
