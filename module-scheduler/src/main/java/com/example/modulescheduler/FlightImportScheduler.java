package com.example.modulescheduler;

import com.example.moduleflight.application.FlightDataMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FlightImportScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(FlightImportScheduler.class);

  private final FlightDataMaintenanceService flightDataMaintenanceService;
  private final boolean runOnStartup;

  public FlightImportScheduler(
      FlightDataMaintenanceService flightDataMaintenanceService,
      @Value("${app.flight-maintenance.run-on-startup:false}") boolean maintenanceRunOnStartup,
      @Value("${app.flight-import.run-on-startup:false}") boolean importRunOnStartup) {
    this.flightDataMaintenanceService = flightDataMaintenanceService;
    this.runOnStartup = maintenanceRunOnStartup || importRunOnStartup;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runMaintenanceOnStartup() {
    if (!runOnStartup) {
      return;
    }
    LOG.info("Flight maintenance on startup.");
    flightDataMaintenanceService.runScheduledMaintenance();
  }

  @Scheduled(cron = "${app.flight-maintenance.cron:0 30 2 * * *}")
  public void runScheduledMaintenance() {
    flightDataMaintenanceService.runScheduledMaintenance();
  }
}
