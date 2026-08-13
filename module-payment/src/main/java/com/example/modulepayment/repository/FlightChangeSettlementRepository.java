package com.example.modulepayment.repository;

import com.example.modulepayment.entity.FlightChangeSettlementEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightChangeSettlementRepository
    extends JpaRepository<FlightChangeSettlementEntity, Long> {

  @Query(
      """
      select s
      from FlightChangeSettlementEntity s
        left join fetch s.items
      where s.id = :id
      """)
  Optional<FlightChangeSettlementEntity> findByIdWithItems(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select s
      from FlightChangeSettlementEntity s
        left join fetch s.items
      where s.id = :id
      """)
  Optional<FlightChangeSettlementEntity> findByIdWithItemsForUpdate(@Param("id") Long id);

  @Query(
      """
      select s
      from FlightChangeSettlementEntity s
        left join fetch s.items
      where s.booking.id = :bookingId
        and s.idempotencyKey = :idempotencyKey
      order by s.id desc
      """)
  Optional<FlightChangeSettlementEntity> findFirstByBookingIdAndIdempotencyKeyWithItems(
      @Param("bookingId") Long bookingId, @Param("idempotencyKey") String idempotencyKey);

  boolean existsByBookingIdAndStatusIn(Long bookingId, List<String> statuses);
}
