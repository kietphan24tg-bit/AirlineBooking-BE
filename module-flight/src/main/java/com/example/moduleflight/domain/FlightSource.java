package com.example.moduleflight.domain;

/** Stored in {@code flights.flight_source}. */
public final class FlightSource {

  public static final String AIRLABS = "AIRLABS";
  public static final String COVERAGE = "COVERAGE";
  public static final String SEEDED = "SEEDED";

  /**
   * One synthetic row created when search had no results (non-coverage routes or before cron
   * filled).
   */
  public static final String SEARCH_FALLBACK = "SEARCH_FALLBACK";

  public static final String MOCK = "MOCK";

  private FlightSource() {}
}
