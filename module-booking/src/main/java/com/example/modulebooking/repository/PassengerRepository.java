package com.example.modulebooking.repository;

import com.example.modulebooking.entity.PassengerEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassengerRepository extends JpaRepository<PassengerEntity, Long> {

  Optional<PassengerEntity> findByIdAndBookingId(Long id, Long bookingId);

  List<PassengerEntity> findByBookingIdOrderById(Long bookingId);
}
