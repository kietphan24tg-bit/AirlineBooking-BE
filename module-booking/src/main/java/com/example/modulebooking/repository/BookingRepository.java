package com.example.modulebooking.repository;

import com.example.modulebooking.entity.BookingEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("moduleBookingRepository")
public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

  Optional<BookingEntity> findByPnrCode(String pnrCode);
}
