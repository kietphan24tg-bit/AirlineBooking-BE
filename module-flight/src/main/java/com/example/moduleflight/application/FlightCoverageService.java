package com.example.moduleflight.application;

import com.example.moduleflight.config.FlightCoverageProperties;
import com.example.moduleflight.domain.FlightSource;
import com.example.moduleflight.entity.AircraftEntity;
import com.example.moduleflight.entity.AircraftLayoutEntity;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.repository.AircraftLayoutRepository;
import com.example.moduleflight.repository.AirlineRepository;
import com.example.moduleflight.repository.AirportRepository;
import com.example.moduleflight.repository.FlightRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Inserts synthetic {@link FlightSource#COVERAGE} flights for configured domestic markets (default
 * {@code VN → VN}) across rolling dates when no flight exists yet for a departure/arrival/date
 * triple, up to {@link FlightCoverageProperties#maxFlightsPerRun} per invocation.
 */
@Service
public class FlightCoverageService {

  private static final Logger LOG = LoggerFactory.getLogger(FlightCoverageService.class);
  private static final Pattern AIRLINE_IATA_PATTERN = Pattern.compile("^[A-Z0-9]{2}$");
  private static final Pattern AIRPORT_IATA_PATTERN = Pattern.compile("^[A-Z]{3}$");
  private static final String STATUS = "SCHEDULED";

  private final FlightCoverageProperties properties;
  private final FlightRepository flightRepository;
  private final AirlineRepository airlineRepository;
  private final AirportRepository airportRepository;
  private final AircraftLayoutRepository aircraftLayoutRepository;
  private final FlightSeatGenerationService flightSeatGenerationService;
  private final FlightFareGenerationService flightFareGenerationService;

  public FlightCoverageService(
      FlightCoverageProperties properties,
      FlightRepository flightRepository,
      AirlineRepository airlineRepository,
      AirportRepository airportRepository,
      AircraftLayoutRepository aircraftLayoutRepository,
      FlightSeatGenerationService flightSeatGenerationService,
      FlightFareGenerationService flightFareGenerationService) {
    this.properties = properties;
    this.flightRepository = flightRepository;
    this.airlineRepository = airlineRepository;
    this.airportRepository = airportRepository;
    this.aircraftLayoutRepository = aircraftLayoutRepository;
    this.flightSeatGenerationService = flightSeatGenerationService;
    this.flightFareGenerationService = flightFareGenerationService;
  }

  @Transactional
  public int fillCoverageGaps() {
    if (!properties.enabled()) {
      LOG.debug("Flight coverage gap-fill is disabled.");
      return 0;
    }

    List<AircraftEntity> aircraftPool = loadAircraftPool();
    List<AirlineEntity> airlinePool = loadAirlinePool();
    List<AirportEntity> airportPool = loadAirportPool();

    if (aircraftPool.isEmpty() || airlinePool.isEmpty() || airportPool.size() < 2) {
      LOG.warn(
          "Flight coverage skipped: need aircraft with layout ({}), airlines ({}), airports ({})",
          aircraftPool.size(),
          airlinePool.size(),
          airportPool.size());
      return 0;
    }

    int rollingDays = Math.max(1, properties.rollingDays());
    int startOffset = Math.max(0, properties.startDateOffsetDays());
    int maxCreated = Math.max(1, properties.maxFlightsPerRun());
    LocalDate startDate = LocalDate.now().plusDays(startOffset);

    int created = 0;
    int n = airportPool.size();
    outer:
    for (int d = 0; d < rollingDays; d++) {
      LocalDate flightDate = startDate.plusDays(d);
      long epoch = flightDate.toEpochDay();
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
          if (created >= maxCreated) {
            break outer;
          }
          if (i == j) {
            continue;
          }
          AirportEntity dep = airportPool.get(i);
          AirportEntity arr = airportPool.get(j);
          if (Objects.equals(dep.getId(), arr.getId())) {
            continue;
          }

          if (flightRepository.existsByDepartureAirport_IdAndArrivalAirport_IdAndFlightDate(
              dep.getId(), arr.getId(), flightDate)) {
            continue;
          }

          AirlineEntity airline = airlinePool.get((i + j + (int) epoch) % airlinePool.size());
          AircraftEntity aircraft = aircraftPool.get((i + d + j) % aircraftPool.size());

          int h = Objects.hash(dep.getId(), arr.getId(), flightDate, airline.getId());
          String flightNumber =
              normalizeText(airline.getCode())
                  + String.format(Locale.ROOT, "%05d", 10_000 + Math.floorMod(h, 90_000));

          if (flightRepository
              .findByAirlineAndFlightNumberAndFlightDate(airline, flightNumber, flightDate)
              .isPresent()) {
            continue;
          }

          int departureHour = 6 + (i % 12);
          int departureMinute = (j % 4) * 15;
          int durationHours = 2 + ((i + j) % 11);
          int durationMinutes = ((i + j) % 3) * 20;

          OffsetDateTime departureTime =
              departureAt(dep, flightDate, LocalTime.of(departureHour, departureMinute));
          OffsetDateTime arrivalTime =
              arrivalAt(arr, departureTime, durationHours, durationMinutes);

          FlightEntity flight = new FlightEntity();
          flight.setAirline(airline);
          flight.setFlightNumber(flightNumber);
          flight.setFlightDate(flightDate);
          flight.setAircraft(aircraft);
          flight.setDepartureAirport(dep);
          flight.setArrivalAirport(arr);
          flight.setDepartureTime(departureTime);
          flight.setArrivalTime(arrivalTime);
          flight.setDepartureTerminal("T" + ((i % 3) + 1));
          flight.setDepartureGate("G" + ((j % 24) + 1));
          flight.setArrivalTerminal("T" + (((i + 1) % 3) + 1));
          flight.setArrivalGate("A" + ((j % 18) + 1));
          flight.setStatus(STATUS);
          flight.setFlightSource(FlightSource.COVERAGE);
          OffsetDateTime now = OffsetDateTime.now();
          flight.setCreatedAt(now);
          flight.setUpdatedAt(now);

          FlightEntity saved = flightRepository.save(flight);
          flightSeatGenerationService.generateForFlight(saved);
          flightFareGenerationService.generateFaresForFlight(saved);
          created++;
        }
      }
    }

    if (created > 0) {
      LOG.info(
          "Flight coverage: created {} synthetic flights (rollingDays={}, airportsInPool={}, cap={})",
          created,
          rollingDays,
          airportPool.size(),
          maxCreated);
    }
    return created;
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
        .sorted(Comparator.comparing(a -> normalizeText(a.getModel()), String::compareToIgnoreCase))
        .collect(Collectors.toList());
  }

  private List<AirlineEntity> loadAirlinePool() {
    var excludedCodes = parseCsvUpper(properties.excludedAirlineCodes());
    var excludedCountries = parseCsvUpper(properties.excludedAirlineCountryCodes());
    return airlineRepository.findAll().stream()
        .filter(
            a -> a != null && AIRLINE_IATA_PATTERN.matcher(normalizeText(a.getCode())).matches())
        .filter(a -> !excludedCodes.contains(normalizeText(a.getCode())))
        .filter(a -> !excludedCountries.contains(normalizeText(a.getCountryCode())))
        .sorted(
            Comparator.comparing((AirlineEntity a) -> normalizeText(a.getCountryCode()))
                .thenComparing(a -> normalizeText(a.getCode())))
        .collect(Collectors.toList());
  }

  private List<AirportEntity> loadAirportPool() {
    var includedCountries = resolveIncludedCountryCodes();
    if (includedCountries.isEmpty()) {
      LOG.warn("Flight coverage: include-airport-country-codes is empty; no airports loaded.");
      return List.of();
    }
    var excludedCountries = parseCsvUpper(properties.excludedAirportCountryCodes());
    Map<Long, AirportEntity> byId = new LinkedHashMap<>();
    for (String countryCode : includedCountries) {
      for (AirportEntity ap :
          airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode(countryCode)) {
        if (ap == null
            || !AIRPORT_IATA_PATTERN.matcher(normalizeText(ap.getCode())).matches()
            || !StringUtils.hasText(ap.getTimeZone())) {
          continue;
        }
        if (excludedCountries.contains(normalizeText(ap.getCountryCode()))) {
          continue;
        }
        byId.putIfAbsent(ap.getId(), ap);
      }
    }
    return byId.values().stream()
        .sorted(
            Comparator.comparing((AirportEntity a) -> normalizeText(a.getCode()))
                .thenComparing(AirportEntity::getId))
        .collect(Collectors.toList());
  }

  private List<String> resolveIncludedCountryCodes() {
    String raw = properties.includeAirportCountryCodes();
    if (!StringUtils.hasText(raw)) {
      return List.of("VN");
    }
    List<String> parsed =
        Arrays.stream(raw.split(","))
            .map(FlightCoverageService::normalizeText)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    return parsed.isEmpty() ? List.of("VN") : parsed;
  }

  private static java.util.Set<String> parseCsvUpper(String raw) {
    if (!StringUtils.hasText(raw)) {
      return java.util.Set.of();
    }
    return java.util.Arrays.stream(raw.split(","))
        .map(FlightCoverageService::normalizeText)
        .filter(StringUtils::hasText)
        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  private static String normalizeText(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static OffsetDateTime departureAt(
      AirportEntity departureAirport, LocalDate flightDate, LocalTime localTime) {
    ZoneId zoneId = resolveZoneId(departureAirport.getTimeZone());
    return flightDate.atTime(localTime).atZone(zoneId).toOffsetDateTime();
  }

  private static OffsetDateTime arrivalAt(
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

  private static ZoneId resolveZoneId(String rawTimeZone) {
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
}
