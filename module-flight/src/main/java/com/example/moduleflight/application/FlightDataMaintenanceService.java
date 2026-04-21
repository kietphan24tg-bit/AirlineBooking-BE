package com.example.moduleflight.application;

import com.example.moduleflight.config.FlightCoverageProperties;
import com.example.moduleflight.config.FlightMaintenanceProperties;
import com.example.moduleflight.config.FlightMockDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs synthetic domestic coverage gap-fill and expiration cleanup in one scheduled cycle (no
 * external flight schedule import).
 */
@Service
public class FlightDataMaintenanceService {

  private static final Logger LOG = LoggerFactory.getLogger(FlightDataMaintenanceService.class);

  private final FlightCoverageService flightCoverageService;
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
    this.flightCoverageService = flightCoverageService;
    this.flightCoverageProperties = flightCoverageProperties;
    this.flightExpirationCleanupService = flightExpirationCleanupService;
    this.flightMaintenanceProperties = flightMaintenanceProperties;
    this.flightMockDataProperties = flightMockDataProperties;
    this.flightInventoryMetadataService = flightInventoryMetadataService;
  }

  /**
   * One maintenance cycle: optional cleanup, then optional coverage (cleanup first frees old rows).
   */
  public void runScheduledMaintenance() {
    flightInventoryMetadataService.repairMetadataForSaleability();
    if (flightMockDataProperties.enabled()) {
      LOG.warn(
          "Flight maintenance: coverage skipped because app.flight-mock.enabled=true. "
              + "Disable flight-mock to allow coverage gap-fill to create additional flights.");
      if (flightMaintenanceProperties.cleanupEnabled()) {
        flightExpirationCleanupService.deleteExpiredFlightsWithoutTickets();
      }
      return;
    }

    if (!flightMaintenanceProperties.cleanupEnabled() && !flightCoverageProperties.enabled()) {
      LOG.debug("Flight maintenance: nothing enabled (coverage, cleanup).");
      return;
    }

    if (flightMaintenanceProperties.cleanupEnabled()) {
      flightExpirationCleanupService.deleteExpiredFlightsWithoutTickets();
    }

    if (flightCoverageProperties.enabled()) {
      flightCoverageService.fillCoverageGaps();
    }
  }
}
