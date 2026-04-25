package com.example.modulebooking.repository;

import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassengerSeatAssignmentRepository
    extends JpaRepository<PassengerSeatAssignmentEntity, Long> {

  boolean existsByPassengerIdAndStatusIn(Long passengerId, Collection<String> statuses);

  boolean existsByFlightSeatIdAndStatusIn(Long flightSeatId, Collection<String> statuses);

  long countByBookingIdAndStatusIn(Long bookingId, Collection<String> statuses);

  @Query(
      """
      select coalesce(sum(psa.surchargeAmount), 0)
      from PassengerSeatAssignmentEntity psa
      where psa.booking.id = :bookingId
        and psa.status in ('RESERVED', 'BOOKED')
      """)
  BigDecimal sumActiveSurchargeByBookingId(@Param("bookingId") Long bookingId);

  @Modifying
  @Query(
      value =
          """
          update passenger_seat_assignments
          set status = 'BOOKED',
              updated_at = :now
          where booking_id = :bookingId
            and status = 'RESERVED'
          """,
      nativeQuery = true)
  int bookReservedByBookingId(@Param("bookingId") Long bookingId, @Param("now") OffsetDateTime now);

  @Modifying
  @Query(
      value =
          """
          update passenger_seat_assignments
          set status = 'RELEASED',
              updated_at = :now
          where booking_id = :bookingId
            and status = 'RESERVED'
          """,
      nativeQuery = true)
  int releaseReservedByBookingId(
      @Param("bookingId") Long bookingId, @Param("now") OffsetDateTime now);

  @Modifying
  @Query(
      value =
          """
          update passenger_seat_assignments psa
          set status = 'RELEASED',
              updated_at = :now
          from flight_seats fs
          where psa.flight_seat_id = fs.id
            and psa.status = 'RESERVED'
            and fs.reserved_until is not null
            and fs.reserved_until < :now
          """,
      nativeQuery = true)
  int releaseExpiredReserved(@Param("now") OffsetDateTime now);
}
