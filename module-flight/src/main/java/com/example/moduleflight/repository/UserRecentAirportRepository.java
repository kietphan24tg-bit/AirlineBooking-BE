package com.example.moduleflight.repository;

import com.example.moduleflight.application.AirportUsageType;
import com.example.moduleflight.entity.UserRecentAirportEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRecentAirportRepository extends JpaRepository<UserRecentAirportEntity, Long> {

  Optional<UserRecentAirportEntity> findByUserIdAndAirport_CodeIgnoreCaseAndUsageType(
      Long userId, String airportCode, AirportUsageType usageType);

  List<UserRecentAirportEntity> findByUserIdOrderByLastUsedAtDesc(Long userId, Pageable pageable);

  List<UserRecentAirportEntity> findByUserIdAndUsageTypeInOrderByLastUsedAtDesc(
      Long userId, List<AirportUsageType> usageTypes, Pageable pageable);

  Optional<UserRecentAirportEntity> findFirstByUserIdAndUsageTypeOrderByLastUsedAtDesc(
      Long userId, AirportUsageType usageType);
}
