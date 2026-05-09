package com.example.modulescheduler;

import com.example.moduleflight.application.FlightImportService;
import com.example.moduleflight.config.FlightImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FlightImportScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(FlightImportScheduler.class);

  private final FlightImportService flightImportService;
  private final FlightImportProperties properties;

  public FlightImportScheduler(
      FlightImportService flightImportService, FlightImportProperties properties) {
    this.flightImportService = flightImportService;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runFlightImportOnStartup() {
    if (!properties.runOnStartup()) {
      return;
    }
    runFlightImport();
  }

  @Scheduled(cron = "${app.flight-import.cron:0 0 2 * * *}")
  public void runFlightImport() {
    if (!properties.enabled()) {
      LOG.debug("Flight import job is disabled by configuration.");
      return;
    }
    flightImportService.importFlights();
  }
}
