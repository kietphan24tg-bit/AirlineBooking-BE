package com.example.modulescheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.moduleflight.application.AirportImportService;
import com.example.moduleflight.config.AirportImportProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AirportImportSchedulerTest {

  @Mock private AirportImportService airportImportService;

  @Test
  void runAirportImport_shouldSkipWhenDisabled() {
    AirportImportProperties properties =
        new AirportImportProperties(
            false,
            "https://airportsapi.com",
            "0 0 3 * * *",
            100,
            20,
            2,
            "medium_airport,large_airport",
            true,
            "country,region",
            "UNKNOWN",
            "UNKNOWN",
            "UTC");
    AirportImportScheduler scheduler = new AirportImportScheduler(airportImportService, properties);

    scheduler.runAirportImport();

    verify(airportImportService, never()).importAirports();
  }

  @Test
  void runAirportImport_shouldCallServiceWhenEnabled() {
    AirportImportProperties properties =
        new AirportImportProperties(
            true,
            "https://airportsapi.com",
            "0 0 3 * * *",
            100,
            20,
            2,
            "medium_airport,large_airport",
            true,
            "country,region",
            "UNKNOWN",
            "UNKNOWN",
            "UTC");
    AirportImportScheduler scheduler = new AirportImportScheduler(airportImportService, properties);

    scheduler.runAirportImport();

    verify(airportImportService).importAirports();
  }
}
