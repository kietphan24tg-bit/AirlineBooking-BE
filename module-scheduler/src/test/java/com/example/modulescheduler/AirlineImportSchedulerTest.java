package com.example.modulescheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.moduleflight.application.AirlineImportService;
import com.example.moduleflight.config.AirlineImportProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AirlineImportSchedulerTest {

  @Mock private AirlineImportService airlineImportService;

  @Test
  void runAirlineImport_shouldSkipWhenDisabled() {
    AirlineImportProperties properties =
        new AirlineImportProperties(
            false,
            "https://airlabs.co",
            "key",
            "0 30 3 * * *",
            20,
            2,
            false,
            "",
            true,
            "code,name");
    AirlineImportScheduler scheduler =
        new AirlineImportScheduler(airlineImportService, properties, false);

    scheduler.runAirlineImport();

    verify(airlineImportService, never()).importAirlines();
  }

  @Test
  void runAirlineImport_shouldCallServiceWhenEnabled() {
    AirlineImportProperties properties =
        new AirlineImportProperties(
            true, "https://airlabs.co", "key", "0 30 3 * * *", 20, 2, false, "", true, "code,name");
    AirlineImportScheduler scheduler =
        new AirlineImportScheduler(airlineImportService, properties, false);

    scheduler.runAirlineImport();

    verify(airlineImportService).importAirlines();
  }
}
