package com.example.modulescheduler;

import com.example.moduleflight.application.FlightDataMaintenanceService;
import com.example.moduleflight.config.FlightMaintenanceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FlightMaintenanceScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(FlightMaintenanceScheduler.class);

  private final FlightDataMaintenanceService flightDataMaintenanceService;
  private final FlightMaintenanceProperties properties;
  private final TaskExecutor taskExecutor;

  public FlightMaintenanceScheduler(
      FlightDataMaintenanceService flightDataMaintenanceService,
      FlightMaintenanceProperties properties,
      @Qualifier("applicationTaskExecutor")
      TaskExecutor taskExecutor) {
    this.flightDataMaintenanceService = flightDataMaintenanceService;
    this.properties = properties;
    this.taskExecutor = taskExecutor;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runMaintenanceOnStartup() {
    if (!properties.runOnStartup()) {
      return;
    }
    taskExecutor.execute(this::runMaintenanceSafely);
  }

  @Scheduled(cron = "${app.flight-maintenance.cron:0 30 2 * * *}")
  public void runScheduledMaintenance() {
    flightDataMaintenanceService.runScheduledMaintenance();
  }

  private void runMaintenanceSafely() {
    try {
      flightDataMaintenanceService.runScheduledMaintenance();
    } catch (Exception exception) {
      LOG.error("Flight maintenance failed: trigger=startup", exception);
    }
  }
}
