package com.example.modulescheduler;

import com.example.moduleflight.application.FlightMockDataService;
import com.example.moduleflight.config.FlightMockDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FlightMockDataScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(FlightMockDataScheduler.class);

  private final FlightMockDataService flightMockDataService;
  private final FlightMockDataProperties properties;

  public FlightMockDataScheduler(
      FlightMockDataService flightMockDataService, FlightMockDataProperties properties) {
    this.flightMockDataService = flightMockDataService;
    this.properties = properties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runMockOnStartup() {
    if (!properties.runOnStartup()) {
      return;
    }
    if (!properties.enabled()) {
      LOG.debug("Flight mock startup reseed is disabled by configuration.");
      return;
    }
    flightMockDataService.reseedMockFlights();
  }

  @Scheduled(cron = "${app.flight-mock.cron:0 0 4 * * *}")
  public void runMockOnSchedule() {
    if (!properties.enabled()) {
      return;
    }
    flightMockDataService.reseedMockFlights();
  }
}
