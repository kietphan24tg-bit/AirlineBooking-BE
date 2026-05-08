package com.example.moduleflight.application;

import com.example.moduleflight.config.FlightCoverageProperties;
import com.example.moduleflight.config.FlightMaintenanceProperties;
import com.example.moduleflight.config.FlightMockDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Runs expiration cleanup for scheduled flight data. Seed creation is handled elsewhere. */
@Service
public class FlightDataMaintenanceService {

  private static final Logger LOG = LoggerFactory.getLogger(FlightDataMaintenanceService.class);

  private final FlightCoverageProperties flightCoverageProperties;
  private final FlightExpirationCleanupService flightExpirationCleanupService;
  private final FlightMaintenanceProperties flightMaintenanceProperties;
  private final FlightMockDataProperties flightMockDataProperties;
  private final FlightInventoryMetadataService flightInventoryMetadataService;

  public FlightDataMaintenanceService(
      FlightCoverageService flightCoverageService,
      FlightCoverageProperties flightCoverageProperties,
      FlightExpirationCleanupService flightExpirationCleanupService,
      FlightMaintenanceProperties flightMaintenanceProperties,
      FlightMockDataProperties flightMockDataProperties,
      FlightInventoryMetadataService flightInventoryMetadataService) {
    this.flightCoverageProperties = flightCoverageProperties;
    this.flightExpirationCleanupService = flightExpirationCleanupService;
    this.flightMaintenanceProperties = flightMaintenanceProperties;
    this.flightMockDataProperties = flightMockDataProperties;
    this.flightInventoryMetadataService = flightInventoryMetadataService;
  }

  /** One maintenance cycle: optional cleanup only. */
  public void runScheduledMaintenance() {
    flightInventoryMetadataService.repairMetadataForSaleability();
    if (flightMockDataProperties.enabled()) {
      if (flightMaintenanceProperties.cleanupEnabled()) {
        flightExpirationCleanupService.deleteExpiredFlightsWithoutTickets();
      }
      return;
    }

    if (!flightMaintenanceProperties.cleanupEnabled()) {
      if (flightCoverageProperties.enabled()) {
        LOG.debug(
            "Flight maintenance: cleanup disabled. app.flight-coverage.* now scopes seeded flight data instead of scheduled gap-fill.");
      } else {
        LOG.debug("Flight maintenance: nothing enabled.");
      }
      return;
    }

    flightExpirationCleanupService.deleteExpiredFlightsWithoutTickets();
  }
}
