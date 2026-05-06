package com.example.modulepayment.repository;

import com.example.modulepayment.entity.TicketFinancialSnapshotEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketFinancialSnapshotRepository
    extends JpaRepository<TicketFinancialSnapshotEntity, Long> {

  @Query(
      """
      select snapshot
      from TicketFinancialSnapshotEntity snapshot
        join fetch snapshot.ticket
        join fetch snapshot.passenger
      where snapshot.booking.id = :bookingId
      order by snapshot.ticket.id
      """)
  List<TicketFinancialSnapshotEntity> findByBookingIdWithDetails(
      @Param("bookingId") Long bookingId);

  Optional<TicketFinancialSnapshotEntity> findByTicketId(Long ticketId);
}
