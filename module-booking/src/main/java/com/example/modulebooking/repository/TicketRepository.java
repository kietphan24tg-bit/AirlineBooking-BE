package com.example.modulebooking.repository;

import com.example.modulebooking.entity.TicketEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<TicketEntity, Long> {

  @Query(
      """
      select t
      from TicketEntity t
        join fetch t.booking b
        join fetch t.passenger p
        join fetch t.flight f
        join fetch f.airline
        join fetch f.departureAirport
        join fetch f.arrivalAirport
        join fetch t.flightFare ff
        join fetch t.fareClass
        left join fetch t.flightSeat
      where b.id = :bookingId
      order by t.id
      """)
  List<TicketEntity> findByBookingIdWithDetails(@Param("bookingId") Long bookingId);

  @Query(
      """
      select t
      from TicketEntity t
        join fetch t.booking b
        join fetch t.passenger p
        join fetch t.flight f
        join fetch f.airline
        join fetch f.departureAirport
        join fetch f.arrivalAirport
        join fetch t.flightFare ff
        join fetch t.fareClass
        left join fetch t.flightSeat
      where b.id = :bookingId
        and t.id in :ticketIds
      order by t.id
      """)
  List<TicketEntity> findByBookingIdAndTicketIdInWithDetails(
      @Param("bookingId") Long bookingId, @Param("ticketIds") List<Long> ticketIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select t
      from TicketEntity t
        join fetch t.booking b
        join fetch t.passenger p
        join fetch t.flight f
        join fetch f.airline
        join fetch f.departureAirport
        join fetch f.arrivalAirport
        join fetch t.flightFare ff
        join fetch t.fareClass
        left join fetch t.flightSeat
      where b.id = :bookingId
        and t.id in :ticketIds
      order by t.id
      """)
  List<TicketEntity> findByBookingIdAndTicketIdInWithDetailsForUpdate(
      @Param("bookingId") Long bookingId, @Param("ticketIds") List<Long> ticketIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select t
      from TicketEntity t
        join fetch t.booking b
        join fetch t.passenger p
        join fetch t.flight f
        join fetch f.airline
        join fetch f.departureAirport
        join fetch f.arrivalAirport
        join fetch t.flightFare ff
        join fetch t.fareClass
        left join fetch t.flightSeat
      where b.id = :bookingId
        and p.id in :passengerIds
      order by t.id
      """)
  List<TicketEntity> findByBookingIdAndPassengerIdInWithDetailsForUpdate(
      @Param("bookingId") Long bookingId, @Param("passengerIds") List<Long> passengerIds);

  long countByBookingIdAndStatusNot(Long bookingId, String status);
}
