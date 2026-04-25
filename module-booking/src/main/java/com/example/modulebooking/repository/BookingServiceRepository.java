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

  List<BookingServiceEntity> findAllByBookingId(Long bookingId);

  void deleteByBookingIdAndPassengerIdIn(Long bookingId, Collection<Long> passengerIds);

  @Query(
      """
      select coalesce(sum(bs.price), 0)
      from BookingServiceEntity bs
      where bs.booking.id = :bookingId
      """)
  BigDecimal sumPriceByBookingId(@Param("bookingId") Long bookingId);
}
