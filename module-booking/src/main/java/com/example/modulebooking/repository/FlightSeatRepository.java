package com.example.modulebooking.repository;

import com.example.modulebooking.entity.FlightSeatEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightSeatRepository extends JpaRepository<FlightSeatEntity, Long> {

  @Query(
      """
      select fs
      from FlightSeatEntity fs
      join fetch fs.seatClass sc
      join fetch fs.seatType st
      join fetch fs.seatCategory cat
      where fs.flight.id = :flightId
      """)
  List<FlightSeatEntity> findSeatMapByFlightId(@Param("flightId") Long flightId);

  @Query(
      value =
          """
          select count(fs.id)
          from flight_seats fs
          where fs.flight_id = :flightId
            and fs.status = 'AVAILABLE'
            and exists (
                select 1
                from fare_class_seat_classes fcsc
                where fcsc.fare_class_id = :fareClassId
                  and fcsc.seat_class_id = fs.seat_class_id
            )
          """,
      nativeQuery = true)
  long countAvailableSeats(
      @Param("flightId") Long flightId, @Param("fareClassId") Long fareClassId);

  @Modifying
  @Query(
      value =
          """
          update flight_seats
          set status = 'RESERVED',
              booking_id = :bookingId,
              reserved_until = :reservedUntil
          where id in (
              select id
              from flight_seats
              where flight_id = :flightId
                and status = 'AVAILABLE'
                and exists (
                    select 1
                    from fare_class_seat_classes fcsc
                    where fcsc.fare_class_id = :fareClassId
                      and fcsc.seat_class_id = flight_seats.seat_class_id
                )
              order by id
              limit :quantity
              for update skip locked
          )
          """,
      nativeQuery = true)
  int reserveForBooking(
      @Param("bookingId") Long bookingId,
      @Param("flightId") Long flightId,
      @Param("fareClassId") Long fareClassId,
      @Param("quantity") int quantity,
      @Param("reservedUntil") OffsetDateTime reservedUntil);

  @Modifying
  @Query(
      value =
          """
          update flight_seats
          set status = 'BOOKED',
              reserved_until = null
          where booking_id = :bookingId
            and status = 'RESERVED'
          """,
      nativeQuery = true)
  int bookReservedForBooking(@Param("bookingId") Long bookingId);

  @Modifying
  @Query(
      value =
          """
          update flight_seats
          set status = 'AVAILABLE',
              booking_id = null,
              reserved_until = null
          where booking_id = :bookingId
            and status = 'RESERVED'
          """,
      nativeQuery = true)
  int releaseReservedForBooking(@Param("bookingId") Long bookingId);

  @Modifying
  @Query(
      value =
          """
          update flight_seats
          set status = 'AVAILABLE',
              booking_id = null,
              reserved_until = null
          where status = 'RESERVED'
            and reserved_until is not null
            and reserved_until < :now
          """,
      nativeQuery = true)
  int releaseExpiredReservations(@Param("now") OffsetDateTime now);

  @Query(
      value =
          """
          select id
          from flight_seats
          where booking_id = :bookingId
          order by id
          """,
      nativeQuery = true)
  List<Long> findSeatIdsByBookingId(@Param("bookingId") Long bookingId);

  @Query(
      value =
          """
          select fs.id
          from flight_seats fs
          where fs.flight_id = :flightId
            and exists (
                select 1
                from fare_class_seat_classes fcsc
                where fcsc.fare_class_id = :fareClassId
                  and fcsc.seat_class_id = fs.seat_class_id
            )
          """,
      nativeQuery = true)
  Set<Long> findAllowedSeatIdsForFare(
      @Param("flightId") Long flightId, @Param("fareClassId") Long fareClassId);
}
