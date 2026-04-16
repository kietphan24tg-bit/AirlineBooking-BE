package com.example.modulebooking.repository;

import com.example.modulebooking.entity.FlightSeatEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightSeatRepository extends JpaRepository<FlightSeatEntity, Long> {

  @Query(
      """
      select count(fs.id)
      from FlightSeatEntity fs
      where fs.flight.id = :flightId
        and fs.fareClass.id = :fareClassId
        and fs.status = 'AVAILABLE'
      """)
  long countAvailableSeats(
      @Param("flightId") Long flightId, @Param("fareClassId") Long fareClassId);

  @Modifying
  @Query(
      value =
          """
          update flight_seats
          set status = 'BOOKED',
              booking_id = :bookingId
          where id in (
              select id
              from flight_seats
              where flight_id = :flightId
                and fare_class_id = :fareClassId
                and status = 'AVAILABLE'
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
      @Param("quantity") int quantity);

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
}
