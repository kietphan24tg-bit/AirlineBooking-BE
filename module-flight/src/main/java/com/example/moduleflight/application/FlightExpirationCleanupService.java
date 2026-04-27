package com.example.moduleflight.application;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes past flights that have no tickets, plus dependent {@code flight_seats} and {@code
 * flight_fares} rows.
 */
@Service
public class FlightExpirationCleanupService {

  private static final Logger LOG = LoggerFactory.getLogger(FlightExpirationCleanupService.class);

  private final JdbcTemplate jdbcTemplate;

  public FlightExpirationCleanupService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Deletes flights with {@code flight_date} strictly before today (server local date) when no
   * ticket references the flight.
   *
   * @return number of deleted flights
   */
  @Transactional
  public int deleteExpiredFlightsWithoutTickets() {
    LocalDate cutoff = LocalDate.now();
    int seatAssignments =
        jdbcTemplate.update(
            """
            delete from passenger_seat_assignments psa
            using flight_seats fs, flights f
            where psa.flight_seat_id = fs.id
              and fs.flight_id = f.id
              and f.flight_date < ?
              and not exists (select 1 from tickets t where t.flight_id = f.id)
            """,
            cutoff);
    int seats =
        jdbcTemplate.update(
            """
            delete from flight_seats fs
            using flights f
            where fs.flight_id = f.id
              and f.flight_date < ?
              and not exists (select 1 from tickets t where t.flight_id = f.id)
            """,
            cutoff);
    int fareHolds =
        jdbcTemplate.update(
            """
            delete from booking_fare_holds bfh
            using flight_fares ff, flights f
            where bfh.flight_fare_id = ff.id
              and ff.flight_id = f.id
              and f.flight_date < ?
              and not exists (select 1 from tickets t where t.flight_id = f.id)
            """,
            cutoff);
    int fares =
        jdbcTemplate.update(
            """
            delete from flight_fares ff
            using flights f
            where ff.flight_id = f.id
              and f.flight_date < ?
              and not exists (select 1 from tickets t where t.flight_id = f.id)
            """,
            cutoff);
    int flights =
        jdbcTemplate.update(
            """
            delete from flights f
            where f.flight_date < ?
              and not exists (select 1 from tickets t where t.flight_id = f.id)
            """,
            cutoff);
    if (flights > 0) {
      LOG.info(
          "Flight cleanup: deleted flights={}, passenger_seat_assignments rows={}, flight_seats rows={}, booking_fare_holds rows={}, flight_fares rows={}, cutoffDate={}",
          flights,
          seatAssignments,
          seats,
          fareHolds,
          fares,
          cutoff);
    }
    return flights;
  }
}
