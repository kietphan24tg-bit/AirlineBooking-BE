package com.example.moduleflight.application;
import com.example.moduleflight.config.FlightMockDataProperties;
import com.example.moduleflight.domain.FlightSource;
import com.example.moduleflight.entity.AircraftEntity;
import com.example.moduleflight.entity.AircraftLayoutEntity;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.repository.AircraftLayoutRepository;
import com.example.moduleflight.repository.AirlineRepository;
import com.example.moduleflight.repository.AirportRepository;
import com.example.moduleflight.repository.FlightFareRepository;
import com.example.moduleflight.repository.FlightRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightMockDataService {

  private static final Logger LOG = LoggerFactory.getLogger(FlightMockDataService.class);
  private static final Pattern AIRLINE_IATA_PATTERN = Pattern.compile("^[A-Z0-9]{2}$");
  private static final Pattern AIRPORT_IATA_PATTERN = Pattern.compile("^[A-Z]{3}$");
  private static final String DEFAULT_STATUS = "SCHEDULED";
  private static final int PROGRESS_LOG_INTERVAL = 10;

  private final FlightMockDataProperties properties;
  private final FlightRepository flightRepository;
  private final AirlineRepository airlineRepository;
  private final AirportRepository airportRepository;
  private final AircraftLayoutRepository aircraftLayoutRepository;
  private final FlightFareRepository flightFareRepository;
  private final FlightSeatGenerationService flightSeatGenerationService;
  private final FlightFareGenerationService flightFareGenerationService;
  private final JdbcTemplate jdbcTemplate;

  public FlightMockDataService(
      FlightMockDataProperties properties,
      FlightRepository flightRepository,
      AirlineRepository airlineRepository,
      AirportRepository airportRepository,
      AircraftLayoutRepository aircraftLayoutRepository,
      FlightFareRepository flightFareRepository,
      FlightSeatGenerationService flightSeatGenerationService,
      FlightFareGenerationService flightFareGenerationService,
      JdbcTemplate jdbcTemplate) {
    this.properties = properties;
    this.flightRepository = flightRepository;
    this.airlineRepository = airlineRepository;
    this.airportRepository = airportRepository;
    this.aircraftLayoutRepository = aircraftLayoutRepository;
    this.flightFareRepository = flightFareRepository;
    this.flightSeatGenerationService = flightSeatGenerationService;
    this.flightFareGenerationService = flightFareGenerationService;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public MockSummary reseedMockFlights() {
    MockAuditSnapshot before = captureAudit("before");
    LOG.info("Flight mock audit before: {}", before.describe());

    if (!properties.enabled()) {
      LOG.debug("Flight mock reseed is disabled by configuration.");
      return MockSummary.disabled(before);
    }

    long ticketCount = queryCount("select count(*) from tickets");
    if (ticketCount > 0 && !properties.forceWhenTicketsExist()) {
      LOG.warn(
          "Flight mock reseed aborted: found {} tickets. Set app.flight-mock.force-when-tickets-exist=true to override.",
          ticketCount);
      return MockSummary.aborted(before, "tickets_exist");
    }

    List<AircraftEntity> aircraftPool = loadAircraftPool();
    List<AirlineEntity> airlinePool = loadAirlinePool();
    List<AirportEntity> airportPool = loadAirportPool();

    if (aircraftPool.isEmpty()) {
      throw new IllegalStateException("Flight mock reseed requires at least one aircraft with layout.");
    }
    if (airlinePool.isEmpty()) {
      throw new IllegalStateException("Flight mock reseed requires at least one global airline candidate.");
    }
    if (airportPool.size() < 2) {
      throw new IllegalStateException("Flight mock reseed requires at least two airport candidates.");
    }

    long currentFlights = flightRepository.count();
    int targetFlightCount = resolveTargetFlightCount(aircraftPool.size());
    LOG.info(
        "Flight mock reseed started: currentFlights={}, targetFlights={}, aircraftWithLayout={}, airlineCandidates={}, airportCandidates={}",
        currentFlights,
        targetFlightCount,
        aircraftPool.size(),
        airlinePool.size(),
        airportPool.size());

    jdbcTemplate.update("delete from flight_seats");
    flightFareRepository.deleteAllInBatch();
    flightRepository.deleteAllInBatch();

    List<FlightEntity> generatedFlights =
        buildMockFlights(targetFlightCount, aircraftPool, airlinePool, airportPool);
    List<FlightEntity> savedFlights = flightRepository.saveAll(generatedFlights);
    int processedFlights = 0;
    for (FlightEntity flight : savedFlights) {
      flightSeatGenerationService.generateForFlight(flight);
      flightFareGenerationService.generateFaresForFlight(flight);
      processedFlights++;
      if (processedFlights % PROGRESS_LOG_INTERVAL == 0 || processedFlights == savedFlights.size()) {
        LOG.info(
            "Flight mock reseed progress: processedFlights={}, totalFlights={}",
            processedFlights,
            savedFlights.size());
      }
    }

    MockAuditSnapshot after = captureAudit("after");
    LOG.info("Flight mock audit after: {}", after.describe());
    return new MockSummary(savedFlights.size(), before, after, "completed");
  }

  private List<AircraftEntity> loadAircraftPool() {
    Map<Long, AircraftEntity> byId = new LinkedHashMap<>();
    for (AircraftLayoutEntity layout : aircraftLayoutRepository.findAll()) {
      if (layout == null || layout.getAircraft() == null || layout.getAircraft().getId() == null) {
        continue;
      }
      AircraftEntity aircraft = layout.getAircraft();
      if (!StringUtils.hasText(aircraft.getModel())) {
        continue;
      }
      byId.putIfAbsent(aircraft.getId(), aircraft);
    }
    return byId.values().stream()
        .sorted(
            java.util.Comparator.comparing(
                aircraft -> normalizeText(aircraft.getModel()), String::compareToIgnoreCase))
        .collect(Collectors.toList());
  }

  private List<AirlineEntity> loadAirlinePool() {
    Set<String> excludedCodes = parseUppercaseCsv(properties.excludedAirlineCodes());
    Set<String> excludedCountryCodes = parseUppercaseCsv(properties.excludedAirlineCountryCodes());
    return airlineRepository.findAll().stream()
        .filter(airline -> airline != null && AIRLINE_IATA_PATTERN.matcher(normalizeText(airline.getCode())).matches())
        .filter(airline -> !excludedCodes.contains(normalizeText(airline.getCode())))
        .filter(
            airline ->
                !excludedCountryCodes.contains(normalizeText(airline.getCountryCode())))
        .sorted(
            java.util.Comparator.comparing(
                    (AirlineEntity airline) -> normalizeText(airline.getCountryCode()),
                    String::compareToIgnoreCase)
                .thenComparing(airline -> normalizeText(airline.getCode()), String::compareToIgnoreCase))
        .collect(Collectors.toList());
  }

  private List<AirportEntity> loadAirportPool() {
    Set<String> excludedCountryCodes = parseUppercaseCsv(properties.excludedAirportCountryCodes());
    List<AirportEntity> airports =
        airportRepository.findAll().stream()
            .filter(
                airport ->
                    airport != null
                        && AIRPORT_IATA_PATTERN.matcher(normalizeText(airport.getCode())).matches())
            .filter(airport -> StringUtils.hasText(airport.getTimeZone()))
            .filter(
                airport ->
                    !excludedCountryCodes.contains(normalizeText(airport.getCountryCode())))
            .sorted(
                java.util.Comparator.comparing(
                        (AirportEntity airport) -> normalizeText(airport.getCountryCode()),
                        String::compareToIgnoreCase)
                    .thenComparing(airport -> normalizeText(airport.getCity()), String::compareToIgnoreCase)
                    .thenComparing(airport -> normalizeText(airport.getCode()), String::compareToIgnoreCase))
            .collect(Collectors.toList());

    Map<String, Integer> pickedPerCountry = new LinkedHashMap<>();
    List<AirportEntity> selected = new ArrayList<>();
    for (AirportEntity airport : airports) {
      String countryKey = normalizeText(airport.getCountryCode());
      if (!StringUtils.hasText(countryKey)) {
        countryKey = normalizeText(airport.getCountry());
      }
      int picked = pickedPerCountry.getOrDefault(countryKey, 0);
      if (picked >= 2) {
        continue;
      }
      selected.add(airport);
      pickedPerCountry.put(countryKey, picked + 1);
    }
    return selected.size() >= 2 ? selected : airports;
  }

  private int resolveTargetFlightCount(int aircraftCount) {
    int configuredTarget = Math.max(0, properties.targetFlightCount());
    int minimumCoverage = aircraftCount * Math.max(1, properties.flightsPerAircraft());
    if (configuredTarget > 0) {
      return Math.max(configuredTarget, minimumCoverage);
    }
    return minimumCoverage;
  }

  private List<FlightEntity> buildMockFlights(
      int targetFlightCount,
      List<AircraftEntity> aircraftPool,
      List<AirlineEntity> airlinePool,
      List<AirportEntity> airportPool) {
    int daySpan = Math.max(1, properties.daysSpan());
    LocalDate startDate = LocalDate.now().plusDays(Math.max(0, properties.startDateOffsetDays()));
    int arrivalOffset = Math.max(1, airportPool.size() / 3);
    List<FlightEntity> flights = new ArrayList<>();

    for (int index = 0; index < targetFlightCount; index++) {
      AircraftEntity aircraft = aircraftPool.get(index % aircraftPool.size());
      AirlineEntity airline = airlinePool.get(index % airlinePool.size());
      AirportEntity departureAirport = airportPool.get(index % airportPool.size());
      AirportEntity arrivalAirport =
          selectArrivalAirport(airportPool, (index + arrivalOffset) % airportPool.size(), departureAirport);

      LocalDate flightDate = startDate.plusDays(index % daySpan);
      int departureHour = 6 + (index % 12);
      int departureMinute = (index % 4) * 15;
      int durationHours = 2 + (index % 11);
      int durationMinutes = (index % 3) * 20;

      OffsetDateTime departureTime =
          departureOffsetDateTime(departureAirport, flightDate, LocalTime.of(departureHour, departureMinute));
      OffsetDateTime arrivalTime =
          arrivalOffsetDateTime(arrivalAirport, departureTime, durationHours, durationMinutes);

      FlightEntity flight = new FlightEntity();
      flight.setAirline(airline);
      flight.setFlightNumber(
          normalizeText(airline.getCode())
              + String.format(Locale.ROOT, "%05d", 10000 + index));
      flight.setFlightDate(flightDate);
      flight.setAircraft(aircraft);
      flight.setDepartureAirport(departureAirport);
      flight.setArrivalAirport(arrivalAirport);
      flight.setDepartureTime(departureTime);
      flight.setArrivalTime(arrivalTime);
      flight.setDepartureTerminal("T" + ((index % 3) + 1));
      flight.setDepartureGate("G" + ((index % 24) + 1));
      flight.setArrivalTerminal("T" + (((index + 1) % 3) + 1));
      flight.setArrivalGate("A" + ((index % 18) + 1));
      flight.setStatus(DEFAULT_STATUS);
      flight.setFlightSource(FlightSource.MOCK);
      flight.setCreatedAt(OffsetDateTime.now());
      flight.setUpdatedAt(OffsetDateTime.now());
      flights.add(flight);
    }
    return flights;
  }

  private AirportEntity selectArrivalAirport(
      List<AirportEntity> airportPool, int startIndex, AirportEntity departureAirport) {
    String departureCode = normalizeText(departureAirport.getCode());
    String departureCountry = normalizeText(departureAirport.getCountryCode());
    for (int offset = 0; offset < airportPool.size(); offset++) {
      AirportEntity candidate = airportPool.get((startIndex + offset) % airportPool.size());
      if (departureCode.equals(normalizeText(candidate.getCode()))) {
        continue;
      }
      if (!departureCountry.equals(normalizeText(candidate.getCountryCode()))) {
        return candidate;
      }
    }
    for (AirportEntity candidate : airportPool) {
      if (!departureCode.equals(normalizeText(candidate.getCode()))) {
        return candidate;
      }
    }
    throw new IllegalStateException("Flight mock reseed could not select a distinct arrival airport.");
  }

  private OffsetDateTime departureOffsetDateTime(
      AirportEntity departureAirport, LocalDate flightDate, LocalTime localTime) {
    ZoneId zoneId = resolveZoneId(departureAirport.getTimeZone());
    return flightDate.atTime(localTime).atZone(zoneId).toOffsetDateTime();
  }

  private OffsetDateTime arrivalOffsetDateTime(
      AirportEntity arrivalAirport,
      OffsetDateTime departureTime,
      int durationHours,
      int durationMinutes) {
    ZoneId arrivalZone = resolveZoneId(arrivalAirport.getTimeZone());
    ZonedDateTime departureZoned = departureTime.atZoneSameInstant(ZoneOffset.UTC);
    return departureZoned
        .plusHours(durationHours)
        .plusMinutes(durationMinutes)
        .withZoneSameInstant(arrivalZone)
        .toOffsetDateTime();
  }

  private ZoneId resolveZoneId(String rawTimeZone) {
    String timeZone = normalizeText(rawTimeZone);
    if (!StringUtils.hasText(timeZone)) {
      return ZoneOffset.UTC;
    }
    try {
      return ZoneId.of(timeZone);
    } catch (Exception ignored) {
      return ZoneOffset.UTC;
    }
  }

  private MockAuditSnapshot captureAudit(String label) {
    return new MockAuditSnapshot(
        label,
        flightRepository.count(),
        airlineRepository.count(),
        airportRepository.count(),
        queryCount("select count(distinct aircraft_id) from aircraft_layout"),
        queryCount("select count(distinct aircraft_id) from flights"),
        queryTopCounts(
            """
            select a.code as key, count(*) as total
            from flights f
            join airlines a on a.id = f.airline_id
            group by a.code
            order by total desc, a.code asc
            limit 8
            """),
        queryTopCounts(
            """
            select ac.model as key, count(*) as total
            from flights f
            join aircraft ac on ac.id = f.aircraft_id
            group by ac.model
            order by total desc, ac.model asc
            limit 8
            """));
  }

  private long queryCount(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0 : value;
  }

  private String queryTopCounts(String sql) {
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
    if (rows.isEmpty()) {
      return "none";
    }
    return rows.stream()
        .map(
            row ->
                String.valueOf(row.get("key"))
                    + "="
                    + String.valueOf(row.get("total")))
        .collect(Collectors.joining(", "));
  }

  private static Set<String> parseUppercaseCsv(String rawValue) {
    if (!StringUtils.hasText(rawValue)) {
      return Set.of();
    }
    return Arrays.stream(rawValue.split(","))
        .map(FlightMockDataService::normalizeText)
        .filter(StringUtils::hasText)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private static String normalizeText(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  public record MockSummary(
      int generatedFlights, MockAuditSnapshot before, MockAuditSnapshot after, String status) {
    static MockSummary disabled(MockAuditSnapshot before) {
      return new MockSummary(0, before, null, "disabled");
    }

    static MockSummary aborted(MockAuditSnapshot before, String status) {
      return new MockSummary(0, before, null, status);
    }
  }

  public record MockAuditSnapshot(
      String label,
      long flightCount,
      long airlineCount,
      long airportCount,
      long aircraftWithLayoutCount,
      long aircraftUsedInFlights,
      String topAirlineCodes,
      String topAircraftModels) {
    public String describe() {
      return "label="
          + label
          + ", flights="
          + flightCount
          + ", airlines="
          + airlineCount
          + ", airports="
          + airportCount
          + ", aircraftWithLayout="
          + aircraftWithLayoutCount
          + ", aircraftUsedInFlights="
          + aircraftUsedInFlights
          + ", topAirlineCodes="
          + topAirlineCodes
          + ", topAircraftModels="
          + topAircraftModels;
    }
  }
}
