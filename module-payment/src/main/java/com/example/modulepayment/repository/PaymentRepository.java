package com.example.modulepayment.repository;

import com.example.modulepayment.entity.PaymentEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

  Optional<PaymentEntity> findByTransactionId(String transactionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PaymentEntity p where p.transactionId = :transactionId")
  Optional<PaymentEntity> findByTransactionIdForUpdate(
      @Param("transactionId") String transactionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PaymentEntity p where p.id = :paymentId")
  Optional<PaymentEntity> findByIdForUpdate(@Param("paymentId") Long paymentId);

  @Query(
      "select p from PaymentEntity p join fetch p.booking b where p.id = :paymentId and b.user.id = :userId")
  Optional<PaymentEntity> findByIdAndBookingUserId(
      @Param("paymentId") Long paymentId, @Param("userId") Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from PaymentEntity p join fetch p.booking b where p.id = :paymentId and b.user.id = :userId")
  Optional<PaymentEntity> findByIdAndBookingUserIdForUpdate(
      @Param("paymentId") Long paymentId, @Param("userId") Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from PaymentEntity p where p.booking.id = :bookingId and p.paymentStatus = :status")
  List<PaymentEntity> findAllByBookingIdAndPaymentStatusForUpdate(
      @Param("bookingId") Long bookingId, @Param("status") String status);

  @Query(
      """
      select p
      from PaymentEntity p
      where p.booking.id = :bookingId
        and p.paymentStatus in :statuses
      order by coalesce(p.paidAt, p.updatedAt) desc, p.id desc
      """)
  List<PaymentEntity> findByBookingIdAndPaymentStatusInOrderByLatest(
      @Param("bookingId") Long bookingId, @Param("statuses") List<String> statuses);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select p
      from PaymentEntity p
      where p.booking.id = :bookingId
        and p.paymentStatus in :statuses
      order by coalesce(p.paidAt, p.updatedAt) desc, p.id desc
      """)
  List<PaymentEntity> findByBookingIdAndPaymentStatusInForUpdateOrderByLatest(
      @Param("bookingId") Long bookingId, @Param("statuses") List<String> statuses);

  boolean existsByBookingIdAndPaymentStatus(Long bookingId, String paymentStatus);

  @Modifying
  @Query(
      value =
          """
          update payments p
          set payment_status = 'EXPIRED',
              updated_at = :now
          from bookings b
          where p.booking_id = b.id
            and p.payment_status = 'PENDING'
            and (b.status = 'EXPIRED'
              or (b.pnr_expires_at is not null and b.pnr_expires_at < :now))
          """,
      nativeQuery = true)
  int expirePendingPaymentsForExpiredBookings(@Param("now") java.time.OffsetDateTime now);
}
