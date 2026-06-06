package com.example.modulescheduler;

import com.example.moduleflight.application.AirportImportService;
import com.example.moduleflight.config.AirportImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AirportImportScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(AirportImportScheduler.class);

  private final AirportImportService airportImportService;
  private final AirportImportProperties properties;
  private final boolean runOnStartup;
  private final TaskExecutor taskExecutor;

  public AirportImportScheduler(
      AirportImportService airportImportService,
      AirportImportProperties properties,
      @Value("${app.airport-import.run-on-startup:false}") boolean runOnStartup,
      @Qualifier("applicationTaskExecutor")
      TaskExecutor taskExecutor) {
    this.airportImportService = airportImportService;
    this.properties = properties;
    this.runOnStartup = runOnStartup;
    this.taskExecutor = taskExecutor;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runAirportImportOnStartup() {
    if (!runOnStartup) {
      return;
    }
    taskExecutor.execute(() -> runAirportImportSafely("startup"));
  }

  @Scheduled(cron = "${app.airport-import.cron:0 0 3 * * *}")
  public void runAirportImport() {
    if (!properties.enabled()) {
      LOG.debug("Airport import job is disabled by configuration.");
      return;
    }
    airportImportService.importAirports();
  }

  private void runAirportImportSafely(String trigger) {
    try {
      LOG.info("Airport import triggered in background: trigger={}", trigger);
      runAirportImport();
    } catch (Exception exception) {
      LOG.error("Airport import failed: trigger={}", trigger, exception);
    }
  }
}
