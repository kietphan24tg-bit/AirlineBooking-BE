package com.example.modulescheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.moduleflight.application.FlightDataMaintenanceService;
import com.example.moduleflight.config.FlightMaintenanceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightMaintenanceSchedulerTest {

  @Mock private FlightDataMaintenanceService flightDataMaintenanceService;

  @Test
  void runMaintenanceOnStartup_shouldSkipWhenDisabled() {
    FlightMaintenanceScheduler scheduler =
        new FlightMaintenanceScheduler(
            flightDataMaintenanceService,
            new FlightMaintenanceProperties("0 30 2 * * *", false, true));

    scheduler.runMaintenanceOnStartup();

    verify(flightDataMaintenanceService, never()).runScheduledMaintenance();
  }

  @Test
  void runMaintenanceOnStartup_shouldCallMaintenanceWhenEnabled() {
    FlightMaintenanceScheduler scheduler =
        new FlightMaintenanceScheduler(
            flightDataMaintenanceService,
            new FlightMaintenanceProperties("0 30 2 * * *", true, true));

    scheduler.runMaintenanceOnStartup();

    verify(flightDataMaintenanceService).runScheduledMaintenance();
  }
}
