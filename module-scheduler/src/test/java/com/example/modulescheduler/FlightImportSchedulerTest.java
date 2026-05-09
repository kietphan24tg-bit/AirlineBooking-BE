package com.example.modulescheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.moduleflight.application.FlightImportService;
import com.example.moduleflight.config.FlightImportProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightImportSchedulerTest {

  @Mock private FlightImportService flightImportService;

  @Test
  void runFlightImportOnStartup_shouldSkipWhenStartupDisabled() {
    FlightImportProperties properties =
        new FlightImportProperties(false, false, "https://airlabs.co", "key", "0 0 2 * * *", 30, 2, "VN,VJ,QH", 7);
    FlightImportScheduler scheduler =
        new FlightImportScheduler(flightImportService, properties);

    scheduler.runFlightImportOnStartup();

    verify(flightImportService, never()).importFlights();
  }

  @Test
  void runFlightImport_shouldSkipWhenDisabled() {
    FlightImportProperties properties =
        new FlightImportProperties(false, true, "https://airlabs.co", "key", "0 0 2 * * *", 30, 2, "VN,VJ,QH", 7);
    FlightImportScheduler scheduler =
        new FlightImportScheduler(flightImportService, properties);

    scheduler.runFlightImport();

    verify(flightImportService, never()).importFlights();
  }

  @Test
  void runFlightImport_shouldCallServiceWhenEnabled() {
    FlightImportProperties properties =
        new FlightImportProperties(true, true, "https://airlabs.co", "key", "0 0 2 * * *", 30, 2, "VN,VJ,QH", 7);
    FlightImportScheduler scheduler =
        new FlightImportScheduler(flightImportService, properties);

    scheduler.runFlightImport();

    verify(flightImportService).importFlights();
  }
}
