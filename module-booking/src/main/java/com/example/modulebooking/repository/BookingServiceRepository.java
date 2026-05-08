package com.example.modulebooking.repository;

import com.example.modulebooking.entity.BookingServiceEntity;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingServiceRepository extends JpaRepository<BookingServiceEntity, Long> {

  interface PassengerServiceTotalView {
    Long getPassengerId();

    BigDecimal getTotalPrice();
  }

  List<BookingServiceEntity> findAllByBookingId(Long bookingId);

  @Query(
      """
      select bs
      from BookingServiceEntity bs
        join fetch bs.passenger
        join fetch bs.service
        left join fetch bs.serviceOption
      where bs.booking.id = :bookingId
      order by bs.id
      """)
  List<BookingServiceEntity> findDetailedByBookingId(@Param("bookingId") Long bookingId);

  void deleteByBookingIdAndPassengerIdIn(Long bookingId, Collection<Long> passengerIds);

  @Query(
      """
      select coalesce(sum(bs.price), 0)
      from BookingServiceEntity bs
      where bs.booking.id = :bookingId
      """)
  BigDecimal sumPriceByBookingId(@Param("bookingId") Long bookingId);

  @Query(
      """
      select bs.passenger.id as passengerId, coalesce(sum(bs.price), 0) as totalPrice
      from BookingServiceEntity bs
      where bs.booking.id = :bookingId
      group by bs.passenger.id
      """)
  List<PassengerServiceTotalView> sumPriceByBookingIdGroupedByPassenger(
      @Param("bookingId") Long bookingId);
}
