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

  public FlightDataMaintenanceService(
      FlightCoverageService flightCoverageService,
      FlightCoverageProperties flightCoverageProperties,
      FlightExpirationCleanupService flightExpirationCleanupService,
      FlightMaintenanceProperties flightMaintenanceProperties,
      FlightMockDataProperties flightMockDataProperties) {
    this.flightCoverageService = flightCoverageService;
    this.flightCoverageProperties = flightCoverageProperties;
    this.flightExpirationCleanupService = flightExpirationCleanupService;
    this.flightMaintenanceProperties = flightMaintenanceProperties;
    this.flightMockDataProperties = flightMockDataProperties;
  }

  /** One maintenance cycle: optional cleanup, then optional coverage (cleanup first frees old rows). */
  public void runScheduledMaintenance() {
    if (flightMockDataProperties.enabled()) {
      LOG.debug("Flight maintenance: coverage skipped because flight-mock is enabled.");
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
