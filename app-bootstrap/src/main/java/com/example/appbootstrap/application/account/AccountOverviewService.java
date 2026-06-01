package com.example.appbootstrap.application.account;

import com.example.appbootstrap.application.booking.UserTripHistoryService;
import com.example.moduleflight.application.AirportPickerItem;
import com.example.moduleflight.application.AirportPickerService;
import com.example.moduleidentity.application.AuthService;
import com.example.moduleidentity.entity.UserEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountOverviewService {

  private static final int RECENT_AIRPORT_LIMIT = 5;

  private final AuthService authService;
  private final UserTripHistoryService userTripHistoryService;
  private final AirportPickerService airportPickerService;

  public AccountOverviewService(
      AuthService authService,
      UserTripHistoryService userTripHistoryService,
      AirportPickerService airportPickerService) {
    this.authService = authService;
    this.userTripHistoryService = userTripHistoryService;
    this.airportPickerService = airportPickerService;
  }

  @Transactional(readOnly = true)
  public AccountOverviewView getOverview(Long userId) {
    UserEntity user = authService.findById(userId);
    UserTripHistoryService.TripStatusCounts tripStats =
        userTripHistoryService.getTripStatusCounts(userId, OffsetDateTime.now());
    List<AirportPickerItem> recentAirports =
        airportPickerService.recent(userId, RECENT_AIRPORT_LIMIT);
    return new AccountOverviewView(user, tripStats, recentAirports);
  }

  public record AccountOverviewView(
      UserEntity user,
      UserTripHistoryService.TripStatusCounts tripStats,
      List<AirportPickerItem> recentAirports) {}
}
