package com.example.modulescheduler;

import com.example.moduleflight.application.FlightImportService;
import com.example.moduleflight.config.FlightImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FlightImportScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(FlightImportScheduler.class);

  private final FlightImportService flightImportService;
  private final FlightImportProperties properties;
  private final TaskExecutor taskExecutor;

  public FlightImportScheduler(
      FlightImportService flightImportService,
      FlightImportProperties properties,
      @Qualifier("applicationTaskExecutor")
      TaskExecutor taskExecutor) {
    this.flightImportService = flightImportService;
    this.properties = properties;
    this.taskExecutor = taskExecutor;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runFlightImportOnStartup() {
    if (!properties.runOnStartup()) {
      return;
    }
    taskExecutor.execute(() -> runFlightImportSafely("startup"));
  }

  @Scheduled(cron = "${app.flight-import.cron:0 0 2 * * *}")
  public void runFlightImport() {
    if (!properties.enabled()) {
      LOG.debug("Flight import job is disabled by configuration.");
      return;
    }
    flightImportService.importFlights();
  }

  private void runFlightImportSafely(String trigger) {
    try {
      LOG.info("Flight import triggered in background: trigger={}", trigger);
      runFlightImport();
    } catch (Exception exception) {
      LOG.error("Flight import failed: trigger={}", trigger, exception);
    }
  }
}
