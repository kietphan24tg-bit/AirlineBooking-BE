package com.example.moduleflight.application;

import com.example.moduleflight.config.FlightCoverageProperties;
import com.example.moduleflight.config.FlightSearchFallbackProperties;
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
 * When search finds no fares for a route and date, inserts exactly one synthetic flight (and
 * seats/fares) so the user always sees a bookable option, unless a flight already exists for that
 * triple (idempotent).
 */
@Service
public class FlightSearchFallbackService {

  private static final Logger LOG = LoggerFactory.getLogger(FlightSearchFallbackService.class);
  private static final Pattern AIRLINE_IATA_PATTERN = Pattern.compile("^[A-Z0-9]{2}$");
  private static final Pattern AIRPORT_IATA_PATTERN = Pattern.compile("^[A-Z]{3}$");
  private static final String STATUS = "SCHEDULED";

  private final FlightSearchFallbackProperties fallbackProperties;
  private final FlightCoverageProperties coverageProperties;
  private final FlightRepository flightRepository;
  private final AirportRepository airportRepository;
  private final AirlineRepository airlineRepository;
  private final AircraftLayoutRepository aircraftLayoutRepository;
  private final FlightSeatGenerationService flightSeatGenerationService;
  private final FlightFareGenerationService flightFareGenerationService;

  public FlightSearchFallbackService(
      FlightSearchFallbackProperties fallbackProperties,
      FlightCoverageProperties coverageProperties,
      FlightRepository flightRepository,
      AirportRepository airportRepository,
      AirlineRepository airlineRepository,
      AircraftLayoutRepository aircraftLayoutRepository,
      FlightSeatGenerationService flightSeatGenerationService,
      FlightFareGenerationService flightFareGenerationService) {
    this.fallbackProperties = fallbackProperties;
    this.coverageProperties = coverageProperties;
    this.flightRepository = flightRepository;
    this.airportRepository = airportRepository;
    this.airlineRepository = airlineRepository;
    this.aircraftLayoutRepository = aircraftLayoutRepository;
    this.flightSeatGenerationService = flightSeatGenerationService;
    this.flightFareGenerationService = flightFareGenerationService;
  }

  /**
   * @return {@code true} if the caller should run the search query again (a row now exists or was
   *     created).
   */
  @Transactional
  public boolean materializeIfEmpty(String fromCode, String toCode, LocalDate flightDate) {
    if (!fallbackProperties.enabled()) {
      return false;
    }
    String from = normalizeText(fromCode);
    String to = normalizeText(toCode);
    if (!StringUtils.hasText(from) || !StringUtils.hasText(to) || from.equals(to)) {
      return false;
    }

    var depOpt = airportRepository.findFirstByCodeIgnoreCase(from);
    var arrOpt = airportRepository.findFirstByCodeIgnoreCase(to);
    if (depOpt.isEmpty() || arrOpt.isEmpty()) {
      return false;
    }
    AirportEntity dep = depOpt.get();
    AirportEntity arr = arrOpt.get();
    if (!AIRPORT_IATA_PATTERN.matcher(normalizeText(dep.getCode())).matches()
        || !StringUtils.hasText(dep.getTimeZone())
        || !AIRPORT_IATA_PATTERN.matcher(normalizeText(arr.getCode())).matches()
        || !StringUtils.hasText(arr.getTimeZone())) {
      return false;
    }

    if (flightRepository.existsByDepartureAirport_IdAndArrivalAirport_IdAndFlightDate(
        dep.getId(), arr.getId(), flightDate)) {
      return true;
    }

    List<AircraftEntity> aircraftPool = loadAircraftPool();
    List<AirlineEntity> airlinePool = loadAirlinePool();
    if (aircraftPool.isEmpty() || airlinePool.isEmpty()) {
      LOG.warn("Flight search fallback skipped: need aircraft with layout and at least one airline.");
      return false;
    }

    AircraftEntity aircraft = aircraftPool.getFirst();
    for (int attempt = 0; attempt < airlinePool.size(); attempt++) {
      AirlineEntity airline = pickAirline(dep, arr, airlinePool, attempt);
      int h = Objects.hash(dep.getId(), arr.getId(), flightDate, airline.getId(), attempt);
      String flightNumber =
          normalizeText(airline.getCode())
              + String.format(Locale.ROOT, "%05d", 20_000 + Math.floorMod(h, 70_000));
      if (flightRepository
          .findByAirlineAndFlightNumberAndFlightDate(airline, flightNumber, flightDate)
          .isPresent()) {
        continue;
      }

      int departureHour = 6 + Math.floorMod(h, 12);
      int departureMinute = Math.floorMod(h / 3, 4) * 15;
      int durationHours = 2 + Math.floorMod(h, 11);
      int durationMinutes = Math.floorMod(h, 3) * 20;

      OffsetDateTime departureTime =
          departureAt(dep, flightDate, LocalTime.of(departureHour, departureMinute));
      OffsetDateTime arrivalTime = arrivalAt(arr, departureTime, durationHours, durationMinutes);

      FlightEntity flight = new FlightEntity();
      flight.setAirline(airline);
      flight.setFlightNumber(flightNumber);
      flight.setFlightDate(flightDate);
      flight.setAircraft(aircraft);
      flight.setDepartureAirport(dep);
      flight.setArrivalAirport(arr);
      flight.setDepartureTime(departureTime);
      flight.setArrivalTime(arrivalTime);
      flight.setDepartureTerminal("T1");
      flight.setDepartureGate("G1");
      flight.setArrivalTerminal("T1");
      flight.setArrivalGate("A1");
      flight.setStatus(STATUS);
      flight.setFlightSource(FlightSource.SEARCH_FALLBACK);
      OffsetDateTime now = OffsetDateTime.now();
      flight.setCreatedAt(now);
      flight.setUpdatedAt(now);

      FlightEntity saved = flightRepository.save(flight);
      flightSeatGenerationService.generateForFlight(saved);
      flightFareGenerationService.generateFaresForFlight(saved);
      LOG.debug(
          "Flight search fallback: created flight id={} {} {}->{} on {}",
          saved.getId(),
          flightNumber,
          dep.getCode(),
          arr.getCode(),
          flightDate);
      return true;
    }

    LOG.warn("Flight search fallback: could not allocate a unique flight number for {} {} on {}", from, to, flightDate);
    return false;
  }

  private AirlineEntity pickAirline(
      AirportEntity dep, AirportEntity arr, List<AirlineEntity> pool, int attempt) {
    boolean domesticVn =
        "VN".equalsIgnoreCase(normalizeText(dep.getCountryCode()))
            && "VN".equalsIgnoreCase(normalizeText(arr.getCountryCode()));
    if (domesticVn) {
      List<AirlineEntity> vn =
          pool.stream()
              .filter(a -> "VN".equalsIgnoreCase(normalizeText(a.getCountryCode())))
              .toList();
      if (!vn.isEmpty()) {
        return vn.get(attempt % vn.size());
      }
    }
    return pool.get(attempt % pool.size());
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
    var excludedCodes = parseCsvUpper(coverageProperties.excludedAirlineCodes());
    var excludedCountries = parseCsvUpper(coverageProperties.excludedAirlineCountryCodes());
    return airlineRepository.findAll().stream()
        .filter(a -> a != null && AIRLINE_IATA_PATTERN.matcher(normalizeText(a.getCode())).matches())
        .filter(a -> !excludedCodes.contains(normalizeText(a.getCode())))
        .filter(a -> !excludedCountries.contains(normalizeText(a.getCountryCode())))
        .sorted(
            Comparator.comparing((AirlineEntity a) -> normalizeText(a.getCountryCode()))
                .thenComparing(a -> normalizeText(a.getCode())))
        .collect(Collectors.toList());
  }

  private static java.util.Set<String> parseCsvUpper(String raw) {
    if (!StringUtils.hasText(raw)) {
      return java.util.Set.of();
    }
    return java.util.Arrays.stream(raw.split(","))
        .map(FlightSearchFallbackService::normalizeText)
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
