package com.example.modulescheduler;

import com.example.moduleflight.application.FlightMockDataService;
import com.example.moduleflight.config.FlightMockDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FlightMockDataScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(FlightMockDataScheduler.class);

  private final FlightMockDataService flightMockDataService;
  private final FlightMockDataProperties properties;
  private final TaskExecutor taskExecutor;

  public FlightMockDataScheduler(
      FlightMockDataService flightMockDataService,
      FlightMockDataProperties properties,
      @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
    this.flightMockDataService = flightMockDataService;
    this.properties = properties;
    this.taskExecutor = taskExecutor;
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
    taskExecutor.execute(() -> runMockSafely("startup"));
  }

  @Scheduled(cron = "${app.flight-mock.cron:0 0 4 * * *}")
  public void runMockOnSchedule() {
    if (!properties.enabled()) {
      return;
    }
    flightMockDataService.reseedMockFlights();
  }

  private void runMockSafely(String trigger) {
    try {
      LOG.info("Flight mock reseed triggered in background: trigger={}", trigger);
      flightMockDataService.reseedMockFlights();
    } catch (Exception exception) {
      LOG.error("Flight mock reseed failed: trigger={}", trigger, exception);
    }
  }
}
