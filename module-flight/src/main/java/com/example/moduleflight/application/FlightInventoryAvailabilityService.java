package com.example.moduleflight.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FlightInventoryAvailabilityService {

  private static final String COUNT_AVAILABLE_SEATS_SQL =
      """
      select count(fs.id)
      from flight_seats fs
      where fs.flight_id = ?
        and fs.status = 'AVAILABLE'
        and exists (
            select 1
            from fare_class_seat_classes fcsc
            where fcsc.fare_class_id = ?
              and fcsc.seat_class_id = fs.seat_class_id
        )
      """;

  private final JdbcTemplate jdbcTemplate;

  public FlightInventoryAvailabilityService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public long countAvailableSeats(Long flightId, Long fareClassId) {
    if (flightId == null || fareClassId == null) {
      return 0;
    }
    Long value =
        jdbcTemplate.queryForObject(COUNT_AVAILABLE_SEATS_SQL, Long.class, flightId, fareClassId);
    return value == null ? 0 : value;
  }

  public boolean hasEnoughSeats(Long flightId, Long fareClassId, int seatedPassengers) {
    if (seatedPassengers <= 0) {
      return true;
    }
    return countAvailableSeats(flightId, fareClassId) >= seatedPassengers;
  }
}
