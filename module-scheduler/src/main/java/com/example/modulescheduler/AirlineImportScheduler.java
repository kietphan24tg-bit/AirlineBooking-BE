package com.example.modulescheduler;

import com.example.moduleflight.application.AirlineImportService;
import com.example.moduleflight.config.AirlineImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AirlineImportScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(AirlineImportScheduler.class);

  private final AirlineImportService airlineImportService;
  private final AirlineImportProperties properties;
  private final boolean runOnStartup;

  public AirlineImportScheduler(
      AirlineImportService airlineImportService,
      AirlineImportProperties properties,
      @Value("${app.airline-import.run-on-startup:false}") boolean runOnStartup) {
    this.airlineImportService = airlineImportService;
    this.properties = properties;
    this.runOnStartup = runOnStartup;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runAirlineImportOnStartup() {
    if (!runOnStartup) {
      return;
    }
    runAirlineImport();
  }

  @Scheduled(cron = "${app.airline-import.cron:0 30 3 * * *}")
  public void runAirlineImport() {
    if (!properties.enabled()) {
      LOG.debug("Airline import job is disabled by configuration.");
      return;
    }
    airlineImportService.importAirlines();
  }
}
