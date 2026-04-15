package com.example.modulescheduler;

import com.example.moduleflight.application.AirportImportService;
import com.example.moduleflight.config.AirportImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AirportImportScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(AirportImportScheduler.class);

  private final AirportImportService airportImportService;
  private final AirportImportProperties properties;

  public AirportImportScheduler(
      AirportImportService airportImportService, AirportImportProperties properties) {
    this.airportImportService = airportImportService;
    this.properties = properties;
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
