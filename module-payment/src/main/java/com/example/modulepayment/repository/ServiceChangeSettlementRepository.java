package com.example.modulepayment.repository;

import com.example.modulepayment.entity.ServiceChangeSettlementEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceChangeSettlementRepository
    extends JpaRepository<ServiceChangeSettlementEntity, Long> {

  @Query(
      """
      select s
      from ServiceChangeSettlementEntity s
      left join fetch s.items
      where s.id = :id
      """)
  Optional<ServiceChangeSettlementEntity> findByIdWithItems(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select s
      from ServiceChangeSettlementEntity s
      left join fetch s.items
      where s.id = :id
      """)
  Optional<ServiceChangeSettlementEntity> findByIdWithItemsForUpdate(@Param("id") Long id);

  @Query(
      """
      select s
      from ServiceChangeSettlementEntity s
      left join fetch s.items
      where s.booking.id = :bookingId
        and s.idempotencyKey = :idempotencyKey
      order by s.id desc
      """)
  Optional<ServiceChangeSettlementEntity> findFirstByBookingIdAndIdempotencyKeyWithItems(
      @Param("bookingId") Long bookingId, @Param("idempotencyKey") String idempotencyKey);
}
