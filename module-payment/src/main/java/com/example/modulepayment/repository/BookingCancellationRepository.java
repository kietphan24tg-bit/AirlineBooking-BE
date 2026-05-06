package com.example.modulepayment.repository;

import com.example.modulepayment.entity.BookingCancellationEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingCancellationRepository extends JpaRepository<BookingCancellationEntity, Long> {

  @Query(
      """
      select cancellation
      from BookingCancellationEntity cancellation
        left join fetch cancellation.items item
        left join fetch item.ticket
        left join fetch item.passenger
      where cancellation.booking.id = :bookingId
        and cancellation.idempotencyKey = :idempotencyKey
      """)
  Optional<BookingCancellationEntity> findByBookingIdAndIdempotencyKeyWithItems(
      @Param("bookingId") Long bookingId, @Param("idempotencyKey") String idempotencyKey);

  @Query(
      """
      select cancellation
      from BookingCancellationEntity cancellation
        join fetch cancellation.booking booking
        left join fetch cancellation.items item
        left join fetch item.ticket
        left join fetch item.passenger
      where cancellation.id = :cancellationId
      """)
  Optional<BookingCancellationEntity> findByIdWithItems(
      @Param("cancellationId") Long cancellationId);
}
