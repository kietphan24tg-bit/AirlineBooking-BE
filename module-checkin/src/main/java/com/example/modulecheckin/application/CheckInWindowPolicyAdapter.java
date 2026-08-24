package com.example.modulecheckin.application;

import com.example.modulebooking.application.CheckInWindowPolicyPort;
import com.example.modulecheckin.config.CheckInProperties;
import org.springframework.stereotype.Component;

@Component
public class CheckInWindowPolicyAdapter implements CheckInWindowPolicyPort {

  private final CheckInProperties checkInProperties;

  public CheckInWindowPolicyAdapter(CheckInProperties checkInProperties) {
    this.checkInProperties = checkInProperties;
  }

  @Override
  public int domesticCheckInOpenHours() {
    return checkInProperties.checkinOpenHours();
  }

  @Override
  public int domesticCheckInCloseMinutes() {
    return checkInProperties.domesticCheckinCloseMinutes();
  }

  @Override
  public int internationalCheckInCloseMinutes() {
    return checkInProperties.internationalCheckinCloseMinutes();
  }
}
