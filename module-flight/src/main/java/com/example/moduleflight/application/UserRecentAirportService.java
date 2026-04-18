package com.example.moduleflight.application;

import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.UserRecentAirportEntity;
import com.example.moduleflight.repository.AirportRepository;
import com.example.moduleflight.repository.UserRecentAirportRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserRecentAirportService {

  private final AirportRepository airportRepository;
  private final UserRecentAirportRepository userRecentAirportRepository;

  public UserRecentAirportService(
      AirportRepository airportRepository,
      UserRecentAirportRepository userRecentAirportRepository) {
    this.airportRepository = airportRepository;
    this.userRecentAirportRepository = userRecentAirportRepository;
  }

  @Transactional
  public void recordSearch(Long userId, String departureAirportCode, String arrivalAirportCode) {
    if (userId == null) {
      return;
    }
    record(userId, departureAirportCode, AirportUsageType.DEPARTURE);
    record(userId, arrivalAirportCode, AirportUsageType.ARRIVAL);
  }

  private void record(Long userId, String airportCode, AirportUsageType usageType) {
    if (!StringUtils.hasText(airportCode)) {
      return;
    }
    AirportEntity airport =
        airportRepository.findFirstByCodeIgnoreCase(airportCode.trim()).orElse(null);
    if (airport == null) {
      return;
    }

    OffsetDateTime now = OffsetDateTime.now();
    UserRecentAirportEntity recent =
        userRecentAirportRepository
            .findByUserIdAndAirport_CodeIgnoreCaseAndUsageType(userId, airport.getCode(), usageType)
            .orElseGet(
                () -> {
                  UserRecentAirportEntity created = new UserRecentAirportEntity();
                  created.setUserId(userId);
                  created.setAirport(airport);
                  created.setUsageType(usageType);
                  created.setCreatedAt(now);
                  created.setSearchCount(0);
                  return created;
                });
    recent.setLastUsedAt(now);
    recent.setUpdatedAt(now);
    recent.setSearchCount(recent.getSearchCount() + 1);
    userRecentAirportRepository.save(recent);
  }
}
