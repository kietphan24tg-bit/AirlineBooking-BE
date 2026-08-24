package com.example.moduleai.tool.support;

import com.example.moduleflight.application.AirportPickerItem;
import com.example.moduleflight.application.AirportPickerService;
import com.example.moduleidentity.application.AuthService;
import com.example.moduleidentity.entity.UserEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountToolService {

  private static final int RECENT_AIRPORT_LIMIT = 5;

  private final AuthService authService;
  private final AirportPickerService airportPickerService;

  public AccountToolService(AuthService authService, AirportPickerService airportPickerService) {
    this.authService = authService;
    this.airportPickerService = airportPickerService;
  }

  @Transactional(readOnly = true)
  public CurrentProfileView currentProfile(Long userId) {
    return toCurrentProfileView(authService.findById(userId));
  }

  @Transactional
  public CurrentProfileView updateProfile(
      Long userId,
      String firstName,
      String lastName,
      LocalDate dob,
      String nationality,
      String identifyNumber) {
    return toCurrentProfileView(
        authService.updateCurrentUserProfile(
            userId, firstName, lastName, dob, nationality, identifyNumber));
  }

  @Transactional(readOnly = true)
  public AccountOverviewView overview(Long userId) {
    return new AccountOverviewView(
        currentProfile(userId), airportPickerService.recent(userId, RECENT_AIRPORT_LIMIT));
  }

  private CurrentProfileView toCurrentProfileView(UserEntity user) {
    return new CurrentProfileView(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getPhone(),
        user.getNationality(),
        user.getIdentifyNumber(),
        user.getDob() == null ? null : user.getDob().toLocalDate(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }

  public record CurrentProfileView(
      Long id,
      String email,
      String firstName,
      String lastName,
      String phone,
      String nationality,
      String identifyNumber,
      LocalDate dob,
      java.time.OffsetDateTime createdAt,
      java.time.OffsetDateTime updatedAt) {}

  public record AccountOverviewView(
      CurrentProfileView profile, List<AirportPickerItem> recentAirports) {}
}
