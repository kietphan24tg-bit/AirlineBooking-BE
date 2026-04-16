package com.example.modulescheduler;

import com.example.moduleflight.application.AirportImportService;
import com.example.moduleflight.config.AirportImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AirportImportScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(AirportImportScheduler.class);

  private final AirportImportService airportImportService;
  private final AirportImportProperties properties;
  private final boolean runOnStartup;

  public AirportImportScheduler(
      AirportImportService airportImportService,
      AirportImportProperties properties,
      @Value("${app.airport-import.run-on-startup:false}") boolean runOnStartup) {
    this.airportImportService = airportImportService;
    this.properties = properties;
    this.runOnStartup = runOnStartup;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runAirportImportOnStartup() {
    if (!runOnStartup) {
      return;
    }
    runAirportImport();
  }

  @Scheduled(cron = "${app.airport-import.cron:0 0 3 * * *}")
  public void runAirportImport() {
    if (!properties.enabled()) {
      LOG.debug("Airport import job is disabled by configuration.");
      return;
    }
    airportImportService.importAirports();
  }
}
