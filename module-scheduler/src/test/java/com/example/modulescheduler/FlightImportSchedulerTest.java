package com.example.modulescheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.moduleflight.application.FlightDataMaintenanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightImportSchedulerTest {

  @Mock private FlightDataMaintenanceService flightDataMaintenanceService;

  @Test
  void runMaintenanceOnStartup_shouldSkipWhenDisabled() {
    FlightImportScheduler scheduler =
        new FlightImportScheduler(flightDataMaintenanceService, false);

    scheduler.runMaintenanceOnStartup();

    verify(flightDataMaintenanceService, never()).runScheduledMaintenance();
  }

  @Test
  void runMaintenanceOnStartup_shouldCallMaintenanceWhenEnabled() {
    FlightImportScheduler scheduler =
        new FlightImportScheduler(flightDataMaintenanceService, true);

    scheduler.runMaintenanceOnStartup();

    verify(flightDataMaintenanceService).runScheduledMaintenance();
  }
}
