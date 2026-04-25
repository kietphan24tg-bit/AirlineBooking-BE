package com.example.modulepayment.repository;

import com.example.modulepayment.entity.PaymentEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from PaymentEntity p where p.booking.id = :bookingId and p.paymentStatus = :status")
  List<PaymentEntity> findAllByBookingIdAndPaymentStatusForUpdate(
      @Param("bookingId") Long bookingId, @Param("status") String status);

  boolean existsByBookingIdAndPaymentStatus(Long bookingId, String paymentStatus);
}
