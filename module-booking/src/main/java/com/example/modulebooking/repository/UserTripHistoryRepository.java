package com.example.modulebooking.repository;

import com.example.modulebooking.entity.BookingFareHoldEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface UserTripHistoryRepository extends Repository<BookingFareHoldEntity, Long> {

  @Query(
      """
      select new com.example.modulebooking.repository.UserTripHistoryRow(
        b.id,
        f.id,
        b.pnrCode,
        b.status,
        h.status,
        f.flightNumber,
        fc.cabinClass,
        departureAirport.code,
        departureAirport.city,
        arrivalAirport.code,
        arrivalAirport.city,
        f.departureTime,
        f.arrivalTime,
        f.flightDate,
        (select count(p.id) from PassengerEntity p where p.booking.id = b.id),
        b.totalAmount,
        b.currency
      )
      from BookingFareHoldEntity h
      join h.booking b
      join h.flightFare ff
      join ff.flight f
      join ff.fareClass fc
      join f.departureAirport departureAirport
      join f.arrivalAirport arrivalAirport
      where b.user.id = :userId
        and (:fromDate is null or f.flightDate >= :fromDate)
        and (:toDate is null or f.flightDate <= :toDate)
        and (
          :query is null
          or lower(b.pnrCode) like lower(concat('%', :query, '%'))
          or lower(f.flightNumber) like lower(concat('%', :query, '%'))
          or lower(departureAirport.code) like lower(concat('%', :query, '%'))
          or lower(arrivalAirport.code) like lower(concat('%', :query, '%'))
          or lower(departureAirport.city) like lower(concat('%', :query, '%'))
          or lower(arrivalAirport.city) like lower(concat('%', :query, '%'))
        )
      """)
  List<UserTripHistoryRow> findTripHistoryRows(
      @Param("userId") Long userId,
      @Param("query") String query,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);
}
