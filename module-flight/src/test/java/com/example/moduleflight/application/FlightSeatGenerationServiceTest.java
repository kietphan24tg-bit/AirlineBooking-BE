package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleflight.entity.AircraftEntity;
import com.example.moduleflight.entity.AircraftLayoutEntity;
import com.example.moduleflight.entity.AircraftLayoutSeatCategoryZoneEntity;
import com.example.moduleflight.entity.AircraftLayoutSeatTypeZoneEntity;
import com.example.moduleflight.entity.AircraftLayoutZoneEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.SeatCategoryEntity;
import com.example.moduleflight.entity.SeatClassEntity;
import com.example.moduleflight.entity.SeatTypeEntity;
import com.example.moduleflight.repository.AircraftLayoutRepository;
import com.example.moduleflight.repository.AircraftLayoutSeatCategoryZoneRepository;
import com.example.moduleflight.repository.AircraftLayoutSeatTypeZoneRepository;
import com.example.moduleflight.repository.AircraftLayoutZoneRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class FlightSeatGenerationServiceTest {

  @Mock private AircraftLayoutRepository aircraftLayoutRepository;
  @Mock private AircraftLayoutZoneRepository aircraftLayoutZoneRepository;
  @Mock private AircraftLayoutSeatTypeZoneRepository aircraftLayoutSeatTypeZoneRepository;
  @Mock private AircraftLayoutSeatCategoryZoneRepository aircraftLayoutSeatCategoryZoneRepository;
  @Mock private JdbcTemplate jdbcTemplate;

  @Test
  void generateForFlight_assignsSeatPositionAndCommercialCategory() {
    FlightSeatGenerationService service =
        new FlightSeatGenerationService(
            aircraftLayoutRepository,
            aircraftLayoutZoneRepository,
            aircraftLayoutSeatTypeZoneRepository,
            aircraftLayoutSeatCategoryZoneRepository,
            jdbcTemplate);

    FlightEntity flight = new FlightEntity();
    flight.setId(55L);
    AircraftEntity aircraft = new AircraftEntity();
    aircraft.setId(9L);
    flight.setAircraft(aircraft);

    AircraftLayoutEntity layout = new AircraftLayoutEntity();
    layout.setId(1L);
    layout.setAircraft(aircraft);
    layout.setTotalRows(6);
    layout.setBusinessRows(1);
    layout.setColumns("ABCDEF");

    when(aircraftLayoutRepository.findByAircraftId(9L)).thenReturn(java.util.Optional.of(layout));
    when(aircraftLayoutZoneRepository.findByAircraftLayoutIdOrderByRowStartAsc(1L))
        .thenReturn(List.of(layoutZone(100L, 1, 1), layoutZone(200L, 2, 6)));
    when(aircraftLayoutSeatTypeZoneRepository.findByAircraftLayoutIdOrderByRowStartAsc(1L))
        .thenReturn(
            List.of(
                typeZone("WINDOW", 11L, "A,F"),
                typeZone("AISLE", 12L, "C,D"),
                typeZone("MIDDLE", 13L, "B,E")));
    when(aircraftLayoutSeatCategoryZoneRepository.findByAircraftLayoutIdOrderByRowStartAsc(1L))
        .thenReturn(
            List.of(
                categoryZone("EXTRA_LEGROOM", 24L, 2, 2),
                categoryZone("PREFERRED", 22L, 3, 3),
                categoryZone("EXIT_ROW", 23L, 4, 4)));
    when(jdbcTemplate.queryForList("select code, id from seat_types"))
        .thenReturn(
            List.of(
                Map.of("code", "WINDOW", "id", 11L),
                Map.of("code", "AISLE", "id", 12L),
                Map.of("code", "MIDDLE", "id", 13L)));
    when(jdbcTemplate.queryForList("select code, id from seat_categories"))
        .thenReturn(
            List.of(
                Map.of("code", "STANDARD", "id", 21L),
                Map.of("code", "PREFERRED", "id", 22L),
                Map.of("code", "EXIT_ROW", "id", 23L),
                Map.of("code", "EXTRA_LEGROOM", "id", 24L)));
    when(jdbcTemplate.batchUpdate(anyString(), anyList())).thenReturn(new int[0]);

    service.generateForFlight(flight);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Object[]>> batchCaptor = ArgumentCaptor.forClass(List.class);
    verify(jdbcTemplate).batchUpdate(anyString(), batchCaptor.capture());

    Map<String, Object[]> rowsBySeatNumber = new HashMap<>();
    for (Object[] row : batchCaptor.getValue()) {
      rowsBySeatNumber.put((String) row[1], row);
    }

    assertSeat(rowsBySeatNumber.get("2A"), 55L, 200L, 11L, 24L);
    assertSeat(rowsBySeatNumber.get("3C"), 55L, 200L, 12L, 22L);
    assertSeat(rowsBySeatNumber.get("4B"), 55L, 200L, 13L, 23L);
    assertSeat(rowsBySeatNumber.get("5E"), 55L, 200L, 13L, 21L);
  }

  private AircraftLayoutZoneEntity layoutZone(Long seatClassId, int rowStart, int rowEnd) {
    SeatClassEntity seatClass = new SeatClassEntity();
    seatClass.setId(seatClassId);

    AircraftLayoutZoneEntity entity = new AircraftLayoutZoneEntity();
    entity.setSeatClass(seatClass);
    entity.setRowStart(rowStart);
    entity.setRowEnd(rowEnd);
    return entity;
  }

  private AircraftLayoutSeatCategoryZoneEntity categoryZone(
      String code, Long seatCategoryId, int rowStart, int rowEnd) {
    SeatCategoryEntity seatCategory = new SeatCategoryEntity();
    seatCategory.setId(seatCategoryId);
    seatCategory.setCode(code);

    AircraftLayoutSeatCategoryZoneEntity entity = new AircraftLayoutSeatCategoryZoneEntity();
    entity.setSeatCategory(seatCategory);
    entity.setRowStart(rowStart);
    entity.setRowEnd(rowEnd);
    return entity;
  }

  private AircraftLayoutSeatTypeZoneEntity typeZone(String code, Long seatTypeId, String columns) {
    SeatTypeEntity seatType = new SeatTypeEntity();
    seatType.setId(seatTypeId);
    seatType.setCode(code);

    AircraftLayoutSeatTypeZoneEntity entity = new AircraftLayoutSeatTypeZoneEntity();
    entity.setSeatType(seatType);
    entity.setRowStart(1);
    entity.setRowEnd(6);
    entity.setColumns(columns);
    return entity;
  }

  private void assertSeat(
      Object[] row, Long flightId, Long seatClassId, Long seatTypeId, Long seatCategoryId) {
    assertEquals(flightId, row[0]);
    assertEquals(seatClassId, row[2]);
    assertEquals(seatTypeId, row[3]);
    assertEquals(seatCategoryId, row[4]);
  }
}
