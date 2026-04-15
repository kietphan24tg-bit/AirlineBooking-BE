  package com.example.moduleflight.application;

  import com.example.moduleflight.entity.FlightFareEntity;
  import com.example.moduleflight.repository.FlightFareRepository;
  import java.time.LocalDate;
  import java.util.ArrayList;
  import java.util.LinkedHashMap;
  import java.util.List;
  import java.util.Locale;
  import java.util.Map;
  import org.springframework.stereotype.Service;
  import org.springframework.transaction.annotation.Transactional;

  @Service
  public class FlightQueryService {

    private static final List<String> SEARCHABLE_FLIGHT_STATUSES =
        List.of("SCHEDULED", "DELAYED", "BOARDING");

    private final FlightFareRepository flightFareRepository;

    public FlightQueryService(FlightFareRepository flightFareRepository) {
      this.flightFareRepository = flightFareRepository;
    }

    @Transactional(readOnly = true)
    public List<List<FlightFareEntity>> findByRouteAndDate(String fromCode, String toCode, LocalDate flightDate) {
      List<FlightFareEntity> rows =
          flightFareRepository.searchFaresByRouteAndDate(
              normalizeCode(fromCode), normalizeCode(toCode), flightDate, SEARCHABLE_FLIGHT_STATUSES);
      Map<Long, List<FlightFareEntity>> groupedByFlight = new LinkedHashMap<>();
      for (FlightFareEntity row : rows) {
        groupedByFlight.computeIfAbsent(row.getFlight().getId(), ignored -> new ArrayList<>()).add(row);
      }
      return new ArrayList<>(groupedByFlight.values());
    }

    private String normalizeCode(String code) {
      return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
  }
