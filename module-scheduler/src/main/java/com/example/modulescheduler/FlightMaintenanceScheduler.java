package com.example.modulescheduler;

import com.example.moduleflight.application.FlightDataMaintenanceService;
import com.example.moduleflight.config.FlightMaintenanceProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FlightMaintenanceScheduler {

  private final FlightDataMaintenanceService flightDataMaintenanceService;
  private final FlightMaintenanceProperties properties;

  public FlightMaintenanceScheduler(
      FlightDataMaintenanceService flightDataMaintenanceService,
      FlightMaintenanceProperties properties) {
    this.flightDataMaintenanceService = flightDataMaintenanceService;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runMaintenanceOnStartup() {
    if (!properties.runOnStartup()) {
      return;
    }
    flightDataMaintenanceService.runScheduledMaintenance();
  }

  @Scheduled(cron = "${app.flight-maintenance.cron:0 30 2 * * *}")
  public void runScheduledMaintenance() {
    flightDataMaintenanceService.runScheduledMaintenance();
  }
}
