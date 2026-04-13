package com.example.modulepayment.repository;

import com.example.modulebooking.entity.BookingEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

  Optional<BookingEntity> findByIdAndUserId(Long id, Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from BookingEntity b where b.id = :id and b.user.id = :userId")
  Optional<BookingEntity> findByIdAndUserIdForUpdate(
      @Param("id") Long id, @Param("userId") Long userId);
}
