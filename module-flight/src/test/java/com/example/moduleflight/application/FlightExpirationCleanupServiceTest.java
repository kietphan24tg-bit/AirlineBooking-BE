package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class FlightExpirationCleanupServiceTest {

  private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
  private final FlightExpirationCleanupService service =
      new FlightExpirationCleanupService(jdbcTemplate);

  @Test
  void deleteExpiredFlightsWithoutTickets_deletesDependentsBeforeParents() {
    when(jdbcTemplate.update(any(String.class), any(LocalDate.class)))
        .thenReturn(3)
        .thenReturn(5)
        .thenReturn(2)
        .thenReturn(4)
        .thenReturn(1);

    int deletedFlights = service.deleteExpiredFlightsWithoutTickets();

    assertEquals(1, deletedFlights);

    InOrder inOrder = inOrder(jdbcTemplate);
    inOrder.verify(jdbcTemplate)
        .update(eq("""
            delete from passenger_seat_assignments psa
            using flight_seats fs, flights f
            where psa.flight_seat_id = fs.id
              and fs.flight_id = f.id
              and f.flight_date < ?
              and not exists (select 1 from tickets t where t.flight_id = f.id)
            """), any(LocalDate.class));
    inOrder.verify(jdbcTemplate)
        .update(eq("""
            delete from flight_seats fs
            using flights f
            where fs.flight_id = f.id
              and f.flight_date < ?
              and not exists (select 1 from tickets t where t.flight_id = f.id)
            """), any(LocalDate.class));
    inOrder.verify(jdbcTemplate)
        .update(eq("""
            delete from booking_fare_holds bfh
            using flight_fares ff, flights f
            where bfh.flight_fare_id = ff.id
              and ff.flight_id = f.id
              and f.flight_date < ?
              and not exists (select 1 from tickets t where t.flight_id = f.id)
            """), any(LocalDate.class));
    inOrder.verify(jdbcTemplate)
        .update(eq("""
            delete from flight_fares ff
            using flights f
            where ff.flight_id = f.id
              and f.flight_date < ?
              and not exists (select 1 from tickets t where t.flight_id = f.id)
            """), any(LocalDate.class));
    inOrder.verify(jdbcTemplate)
        .update(eq("""
            delete from flights f
            where f.flight_date < ?
              and not exists (select 1 from tickets t where t.flight_id = f.id)
            """), any(LocalDate.class));
    verifyNoMoreInteractions(jdbcTemplate);
  }
}
