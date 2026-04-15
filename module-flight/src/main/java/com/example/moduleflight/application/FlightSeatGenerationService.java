package com.example.moduleflight.application;

import com.example.moduleflight.entity.AircraftLayoutEntity;
import com.example.moduleflight.entity.AircraftLayoutSeatTypeZoneEntity;
import com.example.moduleflight.entity.AircraftLayoutZoneEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.repository.AircraftLayoutRepository;
import com.example.moduleflight.repository.AircraftLayoutSeatTypeZoneRepository;
import com.example.moduleflight.repository.AircraftLayoutZoneRepository;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightSeatGenerationService {

  private static final Logger LOG = LoggerFactory.getLogger(FlightSeatGenerationService.class);

  private final AircraftLayoutRepository aircraftLayoutRepository;
  private final AircraftLayoutZoneRepository aircraftLayoutZoneRepository;
  private final AircraftLayoutSeatTypeZoneRepository aircraftLayoutSeatTypeZoneRepository;
  private final JdbcTemplate jdbcTemplate;

  public FlightSeatGenerationService(
      AircraftLayoutRepository aircraftLayoutRepository,
      AircraftLayoutZoneRepository aircraftLayoutZoneRepository,
      AircraftLayoutSeatTypeZoneRepository aircraftLayoutSeatTypeZoneRepository,
      JdbcTemplate jdbcTemplate) {
    this.aircraftLayoutRepository = aircraftLayoutRepository;
    this.aircraftLayoutZoneRepository = aircraftLayoutZoneRepository;
    this.aircraftLayoutSeatTypeZoneRepository = aircraftLayoutSeatTypeZoneRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public void generateForFlight(FlightEntity flight) {
    if (flight == null || flight.getId() == null || flight.getAircraft() == null) {
      return;
    }

    Long aircraftId = flight.getAircraft().getId();
    if (aircraftId == null) {
      return;
    }

    AircraftLayoutEntity layout = aircraftLayoutRepository.findByAircraftId(aircraftId).orElse(null);
    if (layout == null) {
      LOG.warn("Skip seat generation: no aircraft_layout for aircraftId={}", aircraftId);
      return;
    }

    List<String> columns = parseColumns(layout.getColumns());
    if (columns.isEmpty()
        || layout.getTotalRows() == null
        || layout.getTotalRows() <= 0
        || layout.getBusinessRows() == null
        || layout.getBusinessRows() < 0) {
      LOG.warn("Skip seat generation: invalid layout data for aircraftId={}", aircraftId);
      return;
    }

    int totalRows = layout.getTotalRows();
    Map<Long, List<LayoutZone>> zoneCache = new HashMap<>();
    Map<Long, List<LayoutTypeZone>> seatTypeZoneCache = new HashMap<>();
    Map<String, Long> seatTypeIdByCode = loadSeatTypeIds();
    Timestamp createdAt = Timestamp.from(OffsetDateTime.now().toInstant());
    Set<String> generatedSeatNumbers = new LinkedHashSet<>();

    for (int row = 1; row <= totalRows; row++) {
      for (int i = 0; i < columns.size(); i++) {
        String column = columns.get(i);
        String seatNumber = row + column;
        Long seatClassId = resolveSeatClassId(layout, row, column, zoneCache);
        if (seatClassId == null) {
          LOG.warn(
              "Skip seat generation: no seat class resolved for layoutId={}, row={}, column={}",
              layout.getId(),
              row,
              column);
          continue;
        }
        Long seatTypeId =
            resolveSeatTypeId(layout, row, column, columns, seatTypeZoneCache, seatTypeIdByCode);
        if (seatTypeId == null) {
          LOG.warn(
              "Skip seat generation: no seat type resolved for layoutId={}, row={}, column={}",
              layout.getId(),
              row,
              column);
          continue;
        }
        upsertFlightSeat(flight.getId(), seatNumber, seatClassId, seatTypeId, createdAt);
        generatedSeatNumbers.add(seatNumber);
      }
    }

    markRemovedAvailableSeatsUnavailable(flight.getId(), generatedSeatNumbers);
  }

  private void upsertFlightSeat(
      Long flightId, String seatNumber, Long seatClassId, Long seatTypeId, Timestamp createdAt) {
    jdbcTemplate.update(
        """
        insert into flight_seats (flight_id, seat_number, seat_class_id, seat_type_id, status, created_at)
        values (
          ?,
          ?,
          ?,
          ?,
          'AVAILABLE',
          ?
        )
        on conflict (flight_id, seat_number) do update
        set seat_class_id = excluded.seat_class_id,
            seat_type_id = excluded.seat_type_id
        where flight_seats.status = 'AVAILABLE'
        """,
        flightId,
        seatNumber,
        seatClassId,
        seatTypeId,
        createdAt);
  }

  private static List<String> parseColumns(String rawColumns) {
    if (!StringUtils.hasText(rawColumns)) {
      return List.of();
    }
    String normalized = rawColumns.trim().toUpperCase(Locale.ROOT);
    if (normalized.contains(",")) {
      return java.util.Arrays.stream(normalized.split(","))
          .map(String::trim)
          .filter(StringUtils::hasText)
          .collect(Collectors.toList());
    }
    List<String> columns = new ArrayList<>();
    for (char value : normalized.toCharArray()) {
      if (Character.isLetter(value)) {
        columns.add(String.valueOf(value));
      }
    }
    return columns;
  }

  private Long resolveSeatClassId(
      AircraftLayoutEntity layout,
      int row,
      String column,
      Map<Long, List<LayoutZone>> zoneCache) {
    if (layout.getId() == null) {
      return null;
    }
    List<LayoutZone> zones =
        zoneCache.computeIfAbsent(layout.getId(), this::loadLayoutZones);
    for (LayoutZone zone : zones) {
      if (zone.matches(row, column)) {
        return zone.seatClassId();
      }
    }
    return null;
  }

  private List<LayoutZone> loadLayoutZones(Long layoutId) {
    List<AircraftLayoutZoneEntity> entities =
        aircraftLayoutZoneRepository.findByAircraftLayoutIdOrderByRowStartAsc(layoutId);
    List<LayoutZone> zones = new ArrayList<>();
    for (AircraftLayoutZoneEntity entity : entities) {
      if (entity.getSeatClass() == null || entity.getSeatClass().getId() == null) {
        continue;
      }
      zones.add(
          new LayoutZone(
              entity.getSeatClass().getId(),
              entity.getRowStart(),
              entity.getRowEnd(),
              parseColumns(entity.getColumns())));
    }
    return zones;
  }

  private Long resolveSeatTypeId(
      AircraftLayoutEntity layout,
      int row,
      String column,
      List<String> layoutColumns,
      Map<Long, List<LayoutTypeZone>> zoneCache,
      Map<String, Long> seatTypeIdByCode) {
    if (layout.getId() != null) {
      List<LayoutTypeZone> zones = zoneCache.computeIfAbsent(layout.getId(), this::loadSeatTypeZones);
      for (LayoutTypeZone zone : zones) {
        if (zone.matches(row, column)) {
          return zone.seatTypeId();
        }
      }
    }

    return resolveSeatTypeIdByPosition(column, layoutColumns, seatTypeIdByCode);
  }

  private Long resolveSeatTypeIdByPosition(
      String column, List<String> layoutColumns, Map<String, Long> seatTypeIdByCode) {
    int columnIndex = -1;
    int lastIndex = -1;
    String normalized = normalizeColumn(column);

    if (layoutColumns != null && !layoutColumns.isEmpty()) {
      lastIndex = layoutColumns.size() - 1;
      columnIndex = layoutColumns.indexOf(normalized);
    }

    if (columnIndex < 0) {
      columnIndex = alphabeticalColumnIndex(normalized);
      if (columnIndex < 0) {
        return seatTypeIdByCode.get("MIDDLE");
      }
      // Fallback sequence when explicit layout letters are not available.
      lastIndex = 25;
    }

    if (columnIndex == 0 || columnIndex == lastIndex) {
      return seatTypeIdByCode.get("WINDOW");
    }
    if (columnIndex == 1 || columnIndex == lastIndex - 1) {
      return seatTypeIdByCode.get("AISLE");
    }
    return seatTypeIdByCode.get("MIDDLE");
  }

  private int alphabeticalColumnIndex(String normalizedColumn) {
    if (!StringUtils.hasText(normalizedColumn)) {
      return -1;
    }
    char first = normalizedColumn.charAt(0);
    if (first < 'A' || first > 'Z') {
      return -1;
    }
    return first - 'A';
  }

  private void markRemovedAvailableSeatsUnavailable(Long flightId, Set<String> generatedSeatNumbers) {
    if (generatedSeatNumbers == null || generatedSeatNumbers.isEmpty()) {
      jdbcTemplate.update(
          "update flight_seats set status = 'UNAVAILABLE' where flight_id = ? and status = 'AVAILABLE'",
          flightId);
      return;
    }

    String placeholders =
        generatedSeatNumbers.stream().map(value -> "?").collect(Collectors.joining(", "));
    String sql =
        "update flight_seats set status = 'UNAVAILABLE' "
            + "where flight_id = ? and status = 'AVAILABLE' and seat_number not in ("
            + placeholders
            + ")";
    List<Object> params = new ArrayList<>();
    params.add(flightId);
    params.addAll(generatedSeatNumbers);
    jdbcTemplate.update(sql, params.toArray());
  }

  private List<LayoutTypeZone> loadSeatTypeZones(Long layoutId) {
    List<AircraftLayoutSeatTypeZoneEntity> entities =
        aircraftLayoutSeatTypeZoneRepository.findByAircraftLayoutIdOrderByRowStartAsc(layoutId);
    List<LayoutTypeZone> zones = new ArrayList<>();
    for (AircraftLayoutSeatTypeZoneEntity entity : entities) {
      if (entity.getSeatType() == null || entity.getSeatType().getId() == null) {
        continue;
      }
      zones.add(
          new LayoutTypeZone(
              entity.getSeatType().getId(),
              entity.getRowStart(),
              entity.getRowEnd(),
              parseColumns(entity.getColumns())));
    }
    return zones;
  }

  private Map<String, Long> loadSeatTypeIds() {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList("select code, id from seat_types");
    Map<String, Long> ids = new HashMap<>();
    for (Map<String, Object> row : rows) {
      String code = row.get("code") == null ? null : row.get("code").toString();
      Object idValue = row.get("id");
      if (!StringUtils.hasText(code) || idValue == null) {
        continue;
      }
      ids.put(code.toUpperCase(Locale.ROOT), ((Number) idValue).longValue());
    }
    return ids;
  }

  private static String normalizeColumn(String column) {
    if (!StringUtils.hasText(column)) {
      return "";
    }
    return column.trim().toUpperCase(Locale.ROOT);
  }

  private record LayoutZone(Long seatClassId, Integer rowStart, Integer rowEnd, List<String> columns) {
    private boolean matches(int row, String column) {
      if (rowStart == null || rowEnd == null || row < rowStart || row > rowEnd) {
        return false;
      }
      if (columns == null || columns.isEmpty()) {
        return true;
      }
      return columns.contains(normalizeColumn(column));
    }
  }

  private record LayoutTypeZone(Long seatTypeId, Integer rowStart, Integer rowEnd, List<String> columns) {
    private boolean matches(int row, String column) {
      if (rowStart == null || rowEnd == null || row < rowStart || row > rowEnd) {
        return false;
      }
      if (columns == null || columns.isEmpty()) {
        return true;
      }
      return columns.contains(normalizeColumn(column));
    }
  }
}
