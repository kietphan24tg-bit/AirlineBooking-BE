package com.example.modulebooking.repository;

import com.example.modulebooking.entity.BookingContactEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingContactRepository extends JpaRepository<BookingContactEntity, Long> {

  List<BookingContactEntity> findByBookingIdOrderByPrimaryDescIdAsc(Long bookingId);
}
