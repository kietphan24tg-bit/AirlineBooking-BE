package com.example.modulescheduler;

import com.example.moduleflight.application.AirlineImportService;
import com.example.moduleflight.config.AirlineImportProperties;
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
public class AirlineImportScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(AirlineImportScheduler.class);

  private final AirlineImportService airlineImportService;
  private final AirlineImportProperties properties;
  private final boolean runOnStartup;
  private final TaskExecutor taskExecutor;

  public AirlineImportScheduler(
      AirlineImportService airlineImportService,
      AirlineImportProperties properties,
      @Value("${app.airline-import.run-on-startup:false}") boolean runOnStartup,
      @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
    this.airlineImportService = airlineImportService;
    this.properties = properties;
    this.runOnStartup = runOnStartup;
    this.taskExecutor = taskExecutor;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runAirlineImportOnStartup() {
    if (!runOnStartup) {
      return;
    }
    taskExecutor.execute(() -> runAirlineImportSafely("startup"));
  }

  @Scheduled(cron = "${app.airline-import.cron:0 30 3 * * *}")
  public void runAirlineImport() {
    if (!properties.enabled()) {
      LOG.debug("Airline import job is disabled by configuration.");
      return;
    }
    airlineImportService.importAirlines();
  }

  private void runAirlineImportSafely(String trigger) {
    try {
      LOG.info("Airline import triggered in background: trigger={}", trigger);
      runAirlineImport();
    } catch (Exception exception) {
      LOG.error("Airline import failed: trigger={}", trigger, exception);
    }
  }
}
