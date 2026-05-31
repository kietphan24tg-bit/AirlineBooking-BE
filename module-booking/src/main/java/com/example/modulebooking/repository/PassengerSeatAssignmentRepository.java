package com.example.modulebooking.repository;

import com.example.modulebooking.entity.PassengerSeatAssignmentEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassengerSeatAssignmentRepository
    extends JpaRepository<PassengerSeatAssignmentEntity, Long> {

  interface PassengerSeatSurchargeView {
    Long getPassengerId();

    BigDecimal getTotalSurcharge();
  }

  boolean existsByPassengerIdAndStatusIn(Long passengerId, Collection<String> statuses);

  @Query(
      """
      select case when count(psa) > 0 then true else false end
      from PassengerSeatAssignmentEntity psa
      where psa.passenger.id = :passengerId
        and psa.flightSeat.flight.id = :flightId
        and psa.status in :statuses
      """)
  boolean existsByPassengerIdAndFlightIdAndStatusIn(
      @Param("passengerId") Long passengerId,
      @Param("flightId") Long flightId,
      @Param("statuses") Collection<String> statuses);

  boolean existsByFlightSeatIdAndStatusIn(Long flightSeatId, Collection<String> statuses);

  long countByBookingIdAndStatusIn(Long bookingId, Collection<String> statuses);

  @Query(
      """
      select count(psa)
      from PassengerSeatAssignmentEntity psa
      where psa.booking.id = :bookingId
        and psa.flightSeat.flight.id = :flightId
        and psa.status in :statuses
      """)
  long countByBookingIdAndFlightIdAndStatusIn(
      @Param("bookingId") Long bookingId,
      @Param("flightId") Long flightId,
      @Param("statuses") Collection<String> statuses);

  @Query(
      """
      select psa
      from PassengerSeatAssignmentEntity psa
        join fetch psa.passenger
        join fetch psa.flightSeat
      where psa.booking.id = :bookingId
        and psa.status in :statuses
      order by psa.id
      """)
  List<PassengerSeatAssignmentEntity> findByBookingIdAndStatusIn(
      @Param("bookingId") Long bookingId, @Param("statuses") Collection<String> statuses);

  @Query(
      """
      select psa
      from PassengerSeatAssignmentEntity psa
        join fetch psa.passenger
        join fetch psa.flightSeat fs
        join fetch fs.flight
      where psa.booking.id = :bookingId
        and fs.flight.id = :flightId
        and psa.status in :statuses
      order by psa.id
      """)
  List<PassengerSeatAssignmentEntity> findByBookingIdAndFlightIdAndStatusIn(
      @Param("bookingId") Long bookingId,
      @Param("flightId") Long flightId,
      @Param("statuses") Collection<String> statuses);

  @Query(
      """
      select psa
      from PassengerSeatAssignmentEntity psa
        join fetch psa.passenger
        join fetch psa.flightSeat
      where psa.booking.id = :bookingId
        and psa.passenger.id in :passengerIds
        and psa.status in :statuses
      order by psa.id
      """)
  List<PassengerSeatAssignmentEntity> findByBookingIdAndPassengerIdInAndStatusIn(
      @Param("bookingId") Long bookingId,
      @Param("passengerIds") Collection<Long> passengerIds,
      @Param("statuses") Collection<String> statuses);

  @Query(
      """
      select psa
      from PassengerSeatAssignmentEntity psa
      where psa.booking.id = :bookingId
        and psa.passenger.id = :passengerId
        and psa.flightSeat.id = :flightSeatId
        and psa.status in :statuses
      """)
  Optional<PassengerSeatAssignmentEntity> findFirstByActiveAssignment(
      @Param("bookingId") Long bookingId,
      @Param("passengerId") Long passengerId,
      @Param("flightSeatId") Long flightSeatId,
      @Param("statuses") Collection<String> statuses);

  @Query(
      """
      select coalesce(sum(psa.surchargeAmount), 0)
      from PassengerSeatAssignmentEntity psa
      where psa.booking.id = :bookingId
        and psa.status in ('RESERVED', 'BOOKED')
      """)
  BigDecimal sumActiveSurchargeByBookingId(@Param("bookingId") Long bookingId);

  @Query(
      """
      select psa.passenger.id as passengerId, coalesce(sum(psa.surchargeAmount), 0) as totalSurcharge
      from PassengerSeatAssignmentEntity psa
      where psa.booking.id = :bookingId
        and psa.status = 'BOOKED'
      group by psa.passenger.id
      """)
  List<PassengerSeatSurchargeView> sumBookedSurchargeByBookingIdGroupedByPassenger(
      @Param("bookingId") Long bookingId);

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
