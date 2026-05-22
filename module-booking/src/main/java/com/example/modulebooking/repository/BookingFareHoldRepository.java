package com.example.modulebooking.repository;

import com.example.modulebooking.entity.BookingFareHoldEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingFareHoldRepository extends JpaRepository<BookingFareHoldEntity, Long> {

  java.util.List<BookingFareHoldEntity> findByBookingIdOrderByIdAsc(Long bookingId);

  Optional<BookingFareHoldEntity> findFirstByBookingIdAndStatusIn(
      Long bookingId, java.util.Collection<String> statuses);

  @Query(
      """
      select h
      from BookingFareHoldEntity h
        join h.flightFare ff
        join ff.flight f
      where h.booking.id = :bookingId
        and f.id = :flightId
        and h.status in :statuses
      """)
  Optional<BookingFareHoldEntity> findByBookingIdAndFlightIdAndStatusIn(
      @Param("bookingId") Long bookingId,
      @Param("flightId") Long flightId,
      @Param("statuses") Collection<String> statuses);

  @Modifying
  @Query(
      value =
          """
          update booking_fare_holds
          set status = 'CONFIRMED',
              updated_at = :now
          where booking_id = :bookingId
            and status = 'HELD'
          """,
      nativeQuery = true)
  int confirmHeldByBookingId(@Param("bookingId") Long bookingId, @Param("now") OffsetDateTime now);

  @Modifying
  @Query(
      value =
          """
          update booking_fare_holds
          set status = 'RELEASED',
              updated_at = :now
          where booking_id = :bookingId
            and status = 'HELD'
          """,
      nativeQuery = true)
  int releaseHeldByBookingId(@Param("bookingId") Long bookingId, @Param("now") OffsetDateTime now);

  @Modifying
  @Query(
      value =
          """
          update booking_fare_holds
          set expires_at = :expiresAt,
              updated_at = :now
          where booking_id = :bookingId
            and status = 'HELD'
          """,
      nativeQuery = true)
  int extendHeldExpiryByBookingId(
      @Param("bookingId") Long bookingId,
      @Param("expiresAt") OffsetDateTime expiresAt,
      @Param("now") OffsetDateTime now);

  @Modifying
  @Query(
      value =
          """
          update booking_fare_holds
          set status = 'RELEASED',
              updated_at = :now
          where status = 'HELD'
            and expires_at is not null
            and expires_at < :now
          """,
      nativeQuery = true)
  int releaseExpiredHolds(@Param("now") OffsetDateTime now);
}
