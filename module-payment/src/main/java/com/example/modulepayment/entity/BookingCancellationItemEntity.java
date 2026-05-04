package com.example.modulepayment.entity;

import com.example.modulebooking.entity.PassengerEntity;
import com.example.modulebooking.entity.TicketEntity;
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
@Table(name = "booking_cancellation_items")
@Getter
@Setter
@NoArgsConstructor
public class BookingCancellationItemEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cancellation_id", nullable = false)
  private BookingCancellationEntity cancellation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", nullable = false)
  private TicketEntity ticket;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "passenger_id", nullable = false)
  private PassengerEntity passenger;

  @Column(name = "fare_gross_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal fareGrossAmount;

  @Column(name = "promo_allocated_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal promoAllocatedAmount;

  @Column(name = "fare_net_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal fareNetAmount;

  @Column(name = "seat_refund_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal seatRefundAmount;

  @Column(name = "service_refund_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal serviceRefundAmount;

  @Column(name = "refund_fee_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal refundFeeAmount;

  @Column(name = "net_refund_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal netRefundAmount;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
