package com.example.moduleflight.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.moduleflight.config.FlightCoverageProperties;
import com.example.moduleflight.config.FlightMaintenanceProperties;
import com.example.moduleflight.config.FlightMockDataProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FlightDataMaintenanceServiceTest {

  private final FlightCoverageService flightCoverageService =
      Mockito.mock(FlightCoverageService.class);
  private final FlightExpirationCleanupService flightExpirationCleanupService =
      Mockito.mock(FlightExpirationCleanupService.class);
  private final FlightInventoryMetadataService flightInventoryMetadataService =
      Mockito.mock(FlightInventoryMetadataService.class);

  @Test
  void runScheduledMaintenance_skipsCoverageWhenFlightMockIsEnabled() {
    FlightDataMaintenanceService service =
        new FlightDataMaintenanceService(
            flightCoverageService,
            new FlightCoverageProperties(true, 14, 1, "VN", 8000, "", "", ""),
            flightExpirationCleanupService,
            new FlightMaintenanceProperties("0 30 2 * * *", false, true),
            new FlightMockDataProperties(
                true, false, "0 0 4 * * *", false, 0, 3, 14, 1, "", "", ""),
            flightInventoryMetadataService);

    service.runScheduledMaintenance();

    verify(flightInventoryMetadataService).repairMetadataForSaleability();
    verify(flightExpirationCleanupService).deleteExpiredFlightsWithoutTickets();
    verify(flightCoverageService, never()).fillCoverageGaps();
  }
}
