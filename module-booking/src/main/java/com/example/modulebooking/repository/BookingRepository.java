package com.example.modulebooking.repository;

import com.example.modulebooking.entity.BookingEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository("moduleBookingRepository")
public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

  Optional<BookingEntity> findByPnrCode(String pnrCode);

  @Modifying
  @Query(
      value =
          """
          update bookings
          set status = 'EXPIRED',
              updated_at = :now
          where status = 'PENDING_PAYMENT'
            and pnr_expires_at is not null
            and pnr_expires_at < :now
          """,
      nativeQuery = true)
  int expirePendingPaymentBookings(@Param("now") OffsetDateTime now);
}
