package com.example.moduleflight.application;

import com.example.moduleflight.config.FlightImportProperties;
import com.example.moduleflight.entity.AircraftEntity;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.integration.airlabs.AirlabsSchedulesClient;
import com.example.moduleflight.integration.airlabs.dto.AirlabsScheduleItem;
import com.example.moduleflight.repository.AircraftRepository;
import com.example.moduleflight.repository.AirlineRepository;
import com.example.moduleflight.repository.AirportRepository;
import com.example.moduleflight.repository.FlightRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightImportService {

  private static final Logger LOG = LoggerFactory.getLogger(FlightImportService.class);

  private final AirlabsSchedulesClient airlabsSchedulesClient;
  private final FlightRepository flightRepository;
  private final AirlineRepository airlineRepository;
  private final AirportRepository airportRepository;
  private final AircraftRepository aircraftRepository;
  private final FlightSeatGenerationService flightSeatGenerationService;
  private final FlightFareGenerationService flightFareGenerationService;
  private final FlightImportProperties properties;

  public FlightImportService(
      AirlabsSchedulesClient airlabsSchedulesClient,
      FlightRepository flightRepository,
      AirlineRepository airlineRepository,
      AirportRepository airportRepository,
      AircraftRepository aircraftRepository,
      FlightSeatGenerationService flightSeatGenerationService,
      FlightFareGenerationService flightFareGenerationService,
      FlightImportProperties properties) {
    this.airlabsSchedulesClient = airlabsSchedulesClient;
    this.flightRepository = flightRepository;
    this.airlineRepository = airlineRepository;
    this.airportRepository = airportRepository;
    this.aircraftRepository = aircraftRepository;
    this.flightSeatGenerationService = flightSeatGenerationService;
    this.flightFareGenerationService = flightFareGenerationService;
    this.properties = properties;
  }

  @Transactional
  public FlightImportSummary importFlights() {
    if (!StringUtils.hasText(properties.apiKey())) {
      LOG.warn("Flight import skipped: app.flight-import.api-key is not configured.");
      return new FlightImportSummary(0, 0, 0, 0, 0);
    }

    Set<String> airlineCodes = resolveAirlineCodes();
    if (airlineCodes.isEmpty()) {
      LOG.warn(
          "Flight import skipped: no airline codes available (database empty or config filter removed all).");
      return new FlightImportSummary(0, 0, 0, 0, 0);
    }

    LocalDate fromDate = LocalDate.now();
    LocalDate toDate = fromDate.plusDays(Math.max(0, properties.daysAhead()));

    int fetched = 0;
    int inserted = 0;
    int updated = 0;
    int skipped = 0;

    for (String airlineCode : airlineCodes) {
      List<AirlabsScheduleItem> items = airlabsSchedulesClient.fetchSchedules(airlineCode, fromDate);
      fetched += items.size();
      TransformResult transformed = transform(items, fromDate, toDate);
      skipped += transformed.skipped();
      UpsertResult upsert = upsert(transformed.candidates());
      inserted += upsert.inserted();
      updated += upsert.updated();
    }

    FlightImportSummary summary = new FlightImportSummary(fetched, inserted, updated, skipped, 0);
    LOG.info(
        "Flight import finished: fetched={}, inserted={}, updated={}, skipped={}, failed={}",
        summary.fetched(),
        summary.inserted(),
        summary.updated(),
        summary.skipped(),
        summary.failed());
    return summary;
  }

  private Set<String> resolveAirlineCodes() {
    Set<String> dbCodes =
        airlineRepository.findAllCodes().stream()
            .map(FlightImportService::normalizeCode)
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

    Set<String> configuredCodes = parseAirlineCodes(properties.airlineIataCodes());
    if (configuredCodes.isEmpty()) {
      return dbCodes;
    }

    dbCodes.retainAll(configuredCodes);
    return dbCodes;
  }

  private TransformResult transform(
      List<AirlabsScheduleItem> items, LocalDate fromDate, LocalDate toDate) {
    List<AirlabsScheduleItem> source = items == null ? List.of() : items;

    Set<String> airlineCodes =
        source.stream()
            .map(AirlabsScheduleItem::airlineIata)
            .map(FlightImportService::normalizeCode)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());
    Set<String> airportCodes =
        source.stream()
            .flatMap(item -> List.of(item.depIata(), item.arrIata()).stream())
            .map(FlightImportService::normalizeCode)
            .filter(StringUtils::hasText)
            .collect(Collectors.toSet());

    Map<String, AirlineEntity> airlinesByCode = toAirlineByCode(airlineRepository.findByCodeIn(airlineCodes));
    Map<String, AirportEntity> airportsByCode = toAirportByCode(airportRepository.findByCodeIn(airportCodes));

    List<FlightCandidate> candidates = new ArrayList<>();
    int skipped = 0;
    for (AirlabsScheduleItem item : source) {
      Optional<FlightCandidate> candidate =
          toCandidate(item, airlinesByCode, airportsByCode, fromDate, toDate);
      if (candidate.isEmpty()) {
        skipped++;
        continue;
      }
      candidates.add(candidate.get());
    }
    return new TransformResult(candidates, skipped);
  }

  private Optional<FlightCandidate> toCandidate(
      AirlabsScheduleItem item,
      Map<String, AirlineEntity> airlinesByCode,
      Map<String, AirportEntity> airportsByCode,
      LocalDate fromDate,
      LocalDate toDate) {
    if (item == null) {
      return Optional.empty();
    }
    String airlineCode = normalizeCode(item.airlineIata());
    String depCode = normalizeCode(item.depIata());
    String arrCode = normalizeCode(item.arrIata());
    String flightNumber = normalizeFlightNumber(item.flightIata(), item.flightIcao());
    String status = normalizeFlightStatus(item.status());
    OffsetDateTime departureTime = parseOffsetDateTime(item.depTime());
    OffsetDateTime arrivalTime = parseOffsetDateTime(item.arrTime());

    if (!StringUtils.hasText(airlineCode)
        || !StringUtils.hasText(depCode)
        || !StringUtils.hasText(arrCode)
        || !StringUtils.hasText(flightNumber)
        || departureTime == null
        || arrivalTime == null
        || !StringUtils.hasText(status)) {
      return Optional.empty();
    }

    LocalDate flightDate = departureTime.toLocalDate();
    if (flightDate.isBefore(fromDate) || flightDate.isAfter(toDate)) {
      return Optional.empty();
    }

    AirlineEntity airline = airlinesByCode.get(airlineCode);
    AirportEntity departureAirport = airportsByCode.get(depCode);
    AirportEntity arrivalAirport = airportsByCode.get(arrCode);
    AircraftEntity aircraft = resolveAircraft(item.aircraftIcao());
    if (airline == null || departureAirport == null || arrivalAirport == null || aircraft == null) {
      return Optional.empty();
    }

    return Optional.of(
        new FlightCandidate(
            airline,
            flightNumber,
            flightDate,
            aircraft,
            departureAirport,
            arrivalAirport,
            departureTime,
            arrivalTime,
            normalizeText(item.depTerminal()),
            normalizeText(item.depGate()),
            normalizeText(item.arrTerminal()),
            normalizeText(item.arrGate()),
            status));
  }

  private UpsertResult upsert(List<FlightCandidate> candidates) {
    if (candidates.isEmpty()) {
      return new UpsertResult(0, 0);
    }

    int inserted = 0;
    int updated = 0;
    OffsetDateTime now = OffsetDateTime.now();
    List<FlightEntity> toSave = new ArrayList<>();

    for (FlightCandidate candidate : candidates) {
      Optional<FlightEntity> existingOptional =
          flightRepository.findByAirlineAndFlightNumberAndFlightDate(
              candidate.airline(), candidate.flightNumber(), candidate.flightDate());
      if (existingOptional.isPresent()) {
        FlightEntity existing = existingOptional.get();
        applyCandidate(existing, candidate, now, false);
        toSave.add(existing);
        updated++;
      } else {
        FlightEntity created = new FlightEntity();
        applyCandidate(created, candidate, now, true);
        toSave.add(created);
        inserted++;
      }
    }

    List<FlightEntity> saved = flightRepository.saveAll(toSave);
    for (FlightEntity flight : saved) {
      flightSeatGenerationService.generateForFlight(flight);
      flightFareGenerationService.generateFaresForFlight(flight);
    }
    return new UpsertResult(inserted, updated);
  }

  private void applyCandidate(
      FlightEntity target, FlightCandidate candidate, OffsetDateTime now, boolean isCreate) {
    target.setAirline(candidate.airline());
    target.setFlightNumber(candidate.flightNumber());
    target.setFlightDate(candidate.flightDate());
    target.setAircraft(candidate.aircraft());
    target.setDepartureAirport(candidate.departureAirport());
    target.setArrivalAirport(candidate.arrivalAirport());
    target.setDepartureTime(candidate.departureTime());
    target.setArrivalTime(candidate.arrivalTime());
    target.setDepartureTerminal(candidate.departureTerminal());
    target.setDepartureGate(candidate.departureGate());
    target.setArrivalTerminal(candidate.arrivalTerminal());
    target.setArrivalGate(candidate.arrivalGate());
    target.setStatus(candidate.status());
    if (isCreate) {
      target.setCreatedAt(now);
    }
    target.setUpdatedAt(now);
  }

  private Map<String, AirlineEntity> toAirlineByCode(Collection<AirlineEntity> entities) {
    Map<String, AirlineEntity> byCode = new LinkedHashMap<>();
    if (entities == null) {
      return byCode;
    }
    for (AirlineEntity entity : entities) {
      String code = normalizeCode(entity.getCode());
      if (StringUtils.hasText(code)) {
        byCode.put(code, entity);
      }
    }
    return byCode;
  }

  private Map<String, AirportEntity> toAirportByCode(Collection<AirportEntity> entities) {
    Map<String, AirportEntity> byCode = new LinkedHashMap<>();
    if (entities == null) {
      return byCode;
    }
    for (AirportEntity entity : entities) {
      String code = normalizeCode(entity.getCode());
      if (StringUtils.hasText(code)) {
        byCode.put(code, entity);
      }
    }
    return byCode;
  }

  private AircraftEntity resolveAircraft(String aircraftIcao) {
    String normalized = normalizeAircraftModel(aircraftIcao);
    if (!StringUtils.hasText(normalized)) {
      return null;
    }
    return aircraftRepository.findFirstByModelIgnoreCase(normalized).orElse(null);
  }

  private static Set<String> parseAirlineCodes(String configuredCodes) {
    if (!StringUtils.hasText(configuredCodes)) {
      return Set.of();
    }
    return Arrays.stream(configuredCodes.split(","))
        .map(FlightImportService::normalizeCode)
        .filter(StringUtils::hasText)
        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  private static String normalizeFlightNumber(String flightIata, String flightIcao) {
    String code = normalizeText(flightIata);
    if (!StringUtils.hasText(code)) {
      code = normalizeText(flightIcao);
    }
    return code;
  }

  private static String normalizeAircraftModel(String aircraftIcao) {
    String code = normalizeCode(aircraftIcao);
    if (!StringUtils.hasText(code)) {
      return null;
    }
    return switch (code) {
      case "A320" -> "A320-200";
      case "A321" -> "A321-200";
      case "A21N" -> "A321neo";
      case "B789" -> "B787-9";
      case "A359" -> "A350-900";
      case "A333" -> "A330-300";
      default -> null;
    };
  }

  private static String normalizeFlightStatus(String status) {
    String normalized = normalizeCode(status);
    if (!StringUtils.hasText(normalized)) {
      return null;
    }
    return switch (normalized) {
      case "SCHEDULED", "DELAYED", "CANCELLED", "BOARDING", "DEPARTED", "ARRIVED" -> normalized;
      default -> "SCHEDULED";
    };
  }

  private static OffsetDateTime parseOffsetDateTime(String value) {
    String normalized = normalizeText(value);
    if (!StringUtils.hasText(normalized)) {
      return null;
    }
    try {
      return OffsetDateTime.parse(normalized);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static String normalizeCode(String value) {
    String normalized = normalizeText(value);
    return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
  }

  private static String normalizeText(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private record FlightCandidate(
      AirlineEntity airline,
      String flightNumber,
      LocalDate flightDate,
      AircraftEntity aircraft,
      AirportEntity departureAirport,
      AirportEntity arrivalAirport,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime,
      String departureTerminal,
      String departureGate,
      String arrivalTerminal,
      String arrivalGate,
      String status) {}

  private record TransformResult(List<FlightCandidate> candidates, int skipped) {}

  private record UpsertResult(int inserted, int updated) {}

  public record FlightImportSummary(int fetched, int inserted, int updated, int skipped, int failed) {}
}
