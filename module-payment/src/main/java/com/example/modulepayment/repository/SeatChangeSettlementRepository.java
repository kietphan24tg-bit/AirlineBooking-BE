package com.example.modulepayment.repository;

import com.example.modulepayment.entity.SeatChangeSettlementEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatChangeSettlementRepository
    extends JpaRepository<SeatChangeSettlementEntity, Long> {

  @Query(
      """
      select s
      from SeatChangeSettlementEntity s
      left join fetch s.items
      where s.id = :id
      """)
  Optional<SeatChangeSettlementEntity> findByIdWithItems(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select s
      from SeatChangeSettlementEntity s
      left join fetch s.items
      where s.id = :id
      """)
  Optional<SeatChangeSettlementEntity> findByIdWithItemsForUpdate(@Param("id") Long id);
}
