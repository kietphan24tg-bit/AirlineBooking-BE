package com.example.moduleflight.application;

import com.example.moduleflight.config.FlightImportProperties;
import com.example.moduleflight.domain.FlightSource;
import com.example.moduleflight.entity.AircraftEntity;
import com.example.moduleflight.entity.AircraftIcaoMappingEntity;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.integration.airlabs.AirlabsSchedulesClient;
import com.example.moduleflight.integration.airlabs.dto.AirlabsScheduleItem;
import com.example.moduleflight.repository.AircraftIcaoMappingRepository;
import com.example.moduleflight.repository.AircraftRepository;
import com.example.moduleflight.repository.AirlineRepository;
import com.example.moduleflight.repository.AirportRepository;
import com.example.moduleflight.repository.FlightRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FlightImportService {

  private static final Logger LOG = LoggerFactory.getLogger(FlightImportService.class);
  private static final Pattern IATA_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{2}$");
  private static final DateTimeFormatter AIRLABS_LOCAL_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final String DEFAULT_AIRCRAFT_MODEL = "A320-200";

  private final AirlabsSchedulesClient airlabsSchedulesClient;
  private final FlightRepository flightRepository;
  private final AirlineRepository airlineRepository;
  private final AirportRepository airportRepository;
  private final AircraftRepository aircraftRepository;
  private final AircraftIcaoMappingRepository aircraftIcaoMappingRepository;
  private final FlightSeatGenerationService flightSeatGenerationService;
  private final FlightFareGenerationService flightFareGenerationService;
  private final AircraftIcaoFallbackService aircraftIcaoFallbackService;
  private final FlightImportProperties properties;

  public FlightImportService(
      AirlabsSchedulesClient airlabsSchedulesClient,
      FlightRepository flightRepository,
      AirlineRepository airlineRepository,
      AirportRepository airportRepository,
      AircraftRepository aircraftRepository,
      AircraftIcaoMappingRepository aircraftIcaoMappingRepository,
      FlightSeatGenerationService flightSeatGenerationService,
      FlightFareGenerationService flightFareGenerationService,
      AircraftIcaoFallbackService aircraftIcaoFallbackService,
      FlightImportProperties properties) {
    this.airlabsSchedulesClient = airlabsSchedulesClient;
    this.flightRepository = flightRepository;
    this.airlineRepository = airlineRepository;
    this.airportRepository = airportRepository;
    this.aircraftRepository = aircraftRepository;
    this.aircraftIcaoMappingRepository = aircraftIcaoMappingRepository;
    this.flightSeatGenerationService = flightSeatGenerationService;
    this.flightFareGenerationService = flightFareGenerationService;
    this.aircraftIcaoFallbackService = aircraftIcaoFallbackService;
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
    SkipDiagnostics skipDiagnostics = new SkipDiagnostics();
    AircraftResolutionDiagnostics aircraftDiagnostics = new AircraftResolutionDiagnostics();

    for (String airlineCode : airlineCodes) {
      List<AirlabsScheduleItem> items =
          airlabsSchedulesClient.fetchSchedules(airlineCode, fromDate);
      fetched += items.size();
      TransformResult transformed = transform(items, fromDate, toDate);
      skipped += transformed.skipped();
      skipDiagnostics.merge(transformed.skipDiagnostics());
      aircraftDiagnostics.merge(transformed.aircraftDiagnostics());
      UpsertResult upsert = upsert(transformed.candidates());
      inserted += upsert.inserted();
      updated += upsert.updated();
      LOG.info(
          "Flight import aircraft diagnostics: airline={}, topAircraftIcao={},"
              + " fallbackToDefault={}, fallbackMissingCode={}, unmappedAircraftIcaoTop={}",
          airlineCode,
          transformed.aircraftDiagnostics().describeObservedTop(),
          transformed.aircraftDiagnostics().fallbackToDefaultCount(),
          transformed.aircraftDiagnostics().fallbackMissingCodeCount(),
          transformed.aircraftDiagnostics().describeUnmappedTop());
      LOG.info(
          "Flight import progress: airline={}, batchFetched={}, batchInserted={}, batchUpdated={},"
              + " batchSkipped={}, totals fetched={}, inserted={}, updated={}, skipped={}",
          airlineCode,
          items.size(),
          upsert.inserted(),
          upsert.updated(),
          transformed.skipped(),
          fetched,
          inserted,
          updated,
          skipped);
    }

    if (skipDiagnostics.total() > 0) {
      LOG.warn("Flight import skipped candidate diagnostics: {}", skipDiagnostics.describe());
    }
    if (aircraftDiagnostics.observedTotal() > 0) {
      LOG.info(
          "Flight import aircraft diagnostics summary: topAircraftIcao={},"
              + " fallbackToDefault={}, fallbackMissingCode={}, unmappedAircraftIcaoTop={}",
          aircraftDiagnostics.describeObservedTop(),
          aircraftDiagnostics.fallbackToDefaultCount(),
          aircraftDiagnostics.fallbackMissingCodeCount(),
          aircraftDiagnostics.describeUnmappedTop());
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
    Set<String> rawDbCodes =
        airlineRepository.findAllCodes().stream()
            .map(FlightImportService::normalizeCode)
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    Set<String> dbCodes =
        rawDbCodes.stream()
            .filter(FlightImportService::isValidIataCode)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    int droppedDbCodes = rawDbCodes.size() - dbCodes.size();

    Set<String> rawConfiguredCodes = parseAirlineCodes(properties.airlineIataCodes());
    Set<String> configuredCodes =
        rawConfiguredCodes.stream()
            .filter(FlightImportService::isValidIataCode)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    int droppedConfiguredCodes = rawConfiguredCodes.size() - configuredCodes.size();

    if (droppedDbCodes > 0 || droppedConfiguredCodes > 0) {
      LOG.warn(
          "Flight import ignored invalid airline codes: dbDropped={}, configuredDropped={} (expected IATA format: 2 alphanumeric chars).",
          droppedDbCodes,
          droppedConfiguredCodes);
    }

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

    Map<String, AirlineEntity> airlinesByCode =
        toAirlineByCode(airlineRepository.findByCodeIn(airlineCodes));
    Map<String, AirportEntity> airportsByCode =
        toAirportByCode(airportRepository.findByCodeIn(airportCodes));

    List<FlightCandidate> candidates = new ArrayList<>();
    SkipDiagnostics skipDiagnostics = new SkipDiagnostics();
    AircraftResolutionDiagnostics aircraftDiagnostics = new AircraftResolutionDiagnostics();
    for (AirlabsScheduleItem item : source) {
      CandidateResult candidate =
          toCandidate(item, airlinesByCode, airportsByCode, fromDate, toDate, aircraftDiagnostics);
      if (candidate.isSkipped()) {
        skipDiagnostics.add(candidate.skipReason(), candidate.sample());
        continue;
      }
      candidates.add(candidate.candidate());
    }
    return new TransformResult(candidates, skipDiagnostics.total(), skipDiagnostics, aircraftDiagnostics);
  }

  private CandidateResult toCandidate(
      AirlabsScheduleItem item,
      Map<String, AirlineEntity> airlinesByCode,
      Map<String, AirportEntity> airportsByCode,
      LocalDate fromDate,
      LocalDate toDate,
      AircraftResolutionDiagnostics aircraftDiagnostics) {
    if (item == null) {
      return CandidateResult.skipped(SkipReason.INVALID_PAYLOAD, "item=null");
    }
    AircraftResolution aircraftResolution = resolveAircraft(item.aircraftIcao());
    aircraftDiagnostics.record(aircraftResolution);
    String airlineCode = normalizeCode(item.airlineIata());
    String depCode = normalizeCode(item.depIata());
    String arrCode = normalizeCode(item.arrIata());
    String flightNumber = normalizeFlightNumber(item.flightIata(), item.flightIcao());
    String status = normalizeFlightStatus(item.status());

    if (!StringUtils.hasText(airlineCode)
        || !StringUtils.hasText(depCode)
        || !StringUtils.hasText(arrCode)
        || !StringUtils.hasText(flightNumber)
        || !StringUtils.hasText(item.depTime())
        || !StringUtils.hasText(item.arrTime())
        || !StringUtils.hasText(status)) {
      return CandidateResult.skipped(
          SkipReason.INVALID_PAYLOAD,
          scheduleSample(item, airlineCode, depCode, arrCode, normalizeCode(item.aircraftIcao())));
    }

    LocalDate flightDate = parseScheduleDate(item.depTime());
    if (flightDate == null) {
      return CandidateResult.skipped(
          SkipReason.INVALID_PAYLOAD,
          scheduleSample(item, airlineCode, depCode, arrCode, normalizeCode(item.aircraftIcao()))
              + ", invalidDepartureTime="
              + item.depTime());
    }
    if (flightDate.isBefore(fromDate) || flightDate.isAfter(toDate)) {
      return CandidateResult.skipped(
          SkipReason.OUT_OF_DATE_RANGE,
          scheduleSample(item, airlineCode, depCode, arrCode, normalizeCode(item.aircraftIcao()))
              + ", flightDate="
              + flightDate);
    }

    AirlineEntity airline = airlinesByCode.get(airlineCode);
    AirportEntity departureAirport = airportsByCode.get(depCode);
    AirportEntity arrivalAirport = airportsByCode.get(arrCode);
    AircraftEntity aircraft = aircraftResolution.aircraft();
    if (airline == null) {
      return CandidateResult.skipped(
          SkipReason.MISSING_AIRLINE,
          scheduleSample(item, airlineCode, depCode, arrCode, normalizeCode(item.aircraftIcao())));
    }
    if (departureAirport == null || arrivalAirport == null) {
      return CandidateResult.skipped(
          SkipReason.MISSING_AIRPORT,
          scheduleSample(item, airlineCode, depCode, arrCode, normalizeCode(item.aircraftIcao()))
              + ", missingDeparture="
              + (departureAirport == null)
              + ", missingArrival="
              + (arrivalAirport == null));
    }
    if (aircraft == null) {
      return CandidateResult.skipped(
          SkipReason.MISSING_AIRCRAFT,
          scheduleSample(item, airlineCode, depCode, arrCode, normalizeCode(item.aircraftIcao())));
    }

    OffsetDateTime departureTime = parseScheduleOffsetDateTime(item.depTime(), departureAirport);
    OffsetDateTime arrivalTime = parseScheduleOffsetDateTime(item.arrTime(), arrivalAirport);
    if (departureTime == null || arrivalTime == null) {
      return CandidateResult.skipped(
          SkipReason.INVALID_PAYLOAD,
          scheduleSample(item, airlineCode, depCode, arrCode, normalizeCode(item.aircraftIcao()))
              + ", invalidTimes depTime="
              + item.depTime()
              + ", arrTime="
              + item.arrTime());
    }

    return CandidateResult.accepted(
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

    Set<AirlineEntity> airlines = new HashSet<>();
    LocalDate minDate = null;
    LocalDate maxDate = null;
    for (FlightCandidate candidate : candidates) {
      airlines.add(candidate.airline());
      if (minDate == null || candidate.flightDate().isBefore(minDate)) {
        minDate = candidate.flightDate();
      }
      if (maxDate == null || candidate.flightDate().isAfter(maxDate)) {
        maxDate = candidate.flightDate();
      }
    }

    Map<FlightLookupKey, FlightEntity> existingByKey = new LinkedHashMap<>();
    if (!airlines.isEmpty() && minDate != null && maxDate != null) {
      List<FlightEntity> existingFlights =
          flightRepository.findByAirlineInAndFlightDateBetween(airlines, minDate, maxDate);
      for (FlightEntity existing : existingFlights) {
        existingByKey.put(toLookupKey(existing), existing);
      }
    }

    int inserted = 0;
    int updated = 0;
    OffsetDateTime now = OffsetDateTime.now();
    List<FlightEntity> toSave = new ArrayList<>();

    for (FlightCandidate candidate : candidates) {
      FlightEntity existing = existingByKey.get(toLookupKey(candidate));
      if (existing != null) {
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
    target.setFlightSource(FlightSource.AIRLABS);
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

  private AircraftResolution resolveAircraft(String aircraftIcao) {
    String code = normalizeCode(aircraftIcao);
    AircraftEntity defaultAircraft = resolveDefaultAircraft();
    if (!StringUtils.hasText(code) || "UNKNOWN".equals(code)) {
      return new AircraftResolution(defaultAircraft, code, defaultAircraft != null, false, true);
    }
    return aircraftIcaoMappingRepository
        .findByIcaoCode(code)
        .map(AircraftIcaoMappingEntity::getAircraft)
        .map(aircraft -> new AircraftResolution(aircraft, code, false, false, false))
        .orElseGet(
            () -> {
              var family = aircraftIcaoFallbackService.resolveByIcaoFamily(code);
              if (family.isPresent()) {
                return new AircraftResolution(family.get(), code, false, false, false);
              }
              return new AircraftResolution(
                  defaultAircraft, code, defaultAircraft != null, true, false);
            });
  }

  private AircraftEntity resolveDefaultAircraft() {
    return aircraftRepository.findByModelIgnoreCase(DEFAULT_AIRCRAFT_MODEL).orElse(null);
  }

  private static FlightLookupKey toLookupKey(FlightCandidate candidate) {
    Long airlineId = candidate.airline() == null ? null : candidate.airline().getId();
    return new FlightLookupKey(airlineId, candidate.flightNumber(), candidate.flightDate());
  }

  private static FlightLookupKey toLookupKey(FlightEntity flight) {
    Long airlineId = flight.getAirline() == null ? null : flight.getAirline().getId();
    return new FlightLookupKey(airlineId, flight.getFlightNumber(), flight.getFlightDate());
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

  private static LocalDate parseScheduleDate(String value) {
    OffsetDateTime offsetDateTime = parseOffsetDateTime(value);
    if (offsetDateTime != null) {
      return offsetDateTime.toLocalDate();
    }
    LocalDateTime localDateTime = parseLocalDateTime(value);
    return localDateTime == null ? null : localDateTime.toLocalDate();
  }

  private static OffsetDateTime parseScheduleOffsetDateTime(String value, AirportEntity airport) {
    OffsetDateTime offsetDateTime = parseOffsetDateTime(value);
    if (offsetDateTime != null) {
      return offsetDateTime;
    }

    LocalDateTime localDateTime = parseLocalDateTime(value);
    if (localDateTime == null) {
      return null;
    }
    ZoneId zoneId = resolveAirportZoneId(airport);
    return localDateTime.atZone(zoneId).toOffsetDateTime();
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

  private static LocalDateTime parseLocalDateTime(String value) {
    String normalized = normalizeText(value);
    if (!StringUtils.hasText(normalized)) {
      return null;
    }
    try {
      return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    } catch (DateTimeParseException ignored) {
      // AirLabs schedules commonly return local airport time as "yyyy-MM-dd HH:mm".
    }
    try {
      return LocalDateTime.parse(normalized, AIRLABS_LOCAL_DATE_TIME_FORMATTER);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private static ZoneId resolveAirportZoneId(AirportEntity airport) {
    if (airport != null && StringUtils.hasText(airport.getTimeZone())) {
      try {
        return ZoneId.of(airport.getTimeZone().trim());
      } catch (RuntimeException ignored) {
        return ZoneOffset.UTC;
      }
    }
    return ZoneOffset.UTC;
  }

  private static String normalizeCode(String value) {
    String normalized = normalizeText(value);
    return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
  }

  private static boolean isValidIataCode(String code) {
    return StringUtils.hasText(code) && IATA_CODE_PATTERN.matcher(code).matches();
  }

  private static String normalizeText(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private static String scheduleSample(
      AirlabsScheduleItem item,
      String airlineCode,
      String depCode,
      String arrCode,
      String aircraftCode) {
    String flightNumber =
        item == null ? null : normalizeFlightNumber(item.flightIata(), item.flightIcao());
    return "flight="
        + valueOrUnknown(flightNumber)
        + ", airline="
        + valueOrUnknown(airlineCode)
        + ", dep="
        + valueOrUnknown(depCode)
        + ", arr="
        + valueOrUnknown(arrCode)
        + ", aircraft="
        + valueOrUnknown(aircraftCode);
  }

  private static String valueOrUnknown(String value) {
    return StringUtils.hasText(value) ? value : "UNKNOWN";
  }

  private enum SkipReason {
    INVALID_PAYLOAD,
    OUT_OF_DATE_RANGE,
    MISSING_AIRLINE,
    MISSING_AIRPORT,
    MISSING_AIRCRAFT
  }

  private static final class SkipDiagnostics {
    private static final int MAX_SAMPLES_PER_REASON = 5;

    private final Map<SkipReason, Integer> counts = new EnumMap<>(SkipReason.class);
    private final Map<SkipReason, List<String>> samples = new EnumMap<>(SkipReason.class);

    void add(SkipReason reason, String sample) {
      if (reason == null) {
        return;
      }
      counts.merge(reason, 1, Integer::sum);
      if (!StringUtils.hasText(sample)) {
        return;
      }
      List<String> reasonSamples = samples.computeIfAbsent(reason, ignored -> new ArrayList<>());
      if (reasonSamples.size() < MAX_SAMPLES_PER_REASON && !reasonSamples.contains(sample)) {
        reasonSamples.add(sample);
      }
    }

    void merge(SkipDiagnostics other) {
      if (other == null) {
        return;
      }
      for (Map.Entry<SkipReason, Integer> entry : other.counts.entrySet()) {
        counts.merge(entry.getKey(), entry.getValue(), Integer::sum);
      }
      for (Map.Entry<SkipReason, List<String>> entry : other.samples.entrySet()) {
        for (String sample : entry.getValue()) {
          addSampleOnly(entry.getKey(), sample);
        }
      }
    }

    int total() {
      return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    String describe() {
      List<String> parts = new ArrayList<>();
      for (SkipReason reason : SkipReason.values()) {
        Integer count = counts.get(reason);
        if (count == null || count == 0) {
          continue;
        }
        List<String> reasonSamples = samples.getOrDefault(reason, List.of());
        parts.add(reason + "=" + count + ", samples=" + reasonSamples);
      }
      return String.join("; ", parts);
    }

    private void addSampleOnly(SkipReason reason, String sample) {
      if (reason == null || !StringUtils.hasText(sample)) {
        return;
      }
      List<String> reasonSamples = samples.computeIfAbsent(reason, ignored -> new ArrayList<>());
      if (reasonSamples.size() < MAX_SAMPLES_PER_REASON && !reasonSamples.contains(sample)) {
        reasonSamples.add(sample);
      }
    }
  }

  private record CandidateResult(FlightCandidate candidate, SkipReason skipReason, String sample) {
    static CandidateResult accepted(FlightCandidate candidate) {
      return new CandidateResult(candidate, null, null);
    }

    static CandidateResult skipped(SkipReason reason, String sample) {
      return new CandidateResult(null, reason, sample);
    }

    boolean isSkipped() {
      return skipReason != null;
    }
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

  private record TransformResult(
      List<FlightCandidate> candidates,
      int skipped,
      SkipDiagnostics skipDiagnostics,
      AircraftResolutionDiagnostics aircraftDiagnostics) {}

  private record UpsertResult(int inserted, int updated) {}

  private record FlightLookupKey(Long airlineId, String flightNumber, LocalDate flightDate) {}

  private record AircraftResolution(
      AircraftEntity aircraft,
      String normalizedCode,
      boolean fallbackToDefault,
      boolean unmappedCode,
      boolean missingCode) {}

  private static final class AircraftResolutionDiagnostics {
    private static final int MAX_TOP_CODES = 5;

    private final Map<String, Integer> observedCounts = new LinkedHashMap<>();
    private final Map<String, Integer> unmappedCounts = new LinkedHashMap<>();
    private int fallbackToDefaultCount;
    private int fallbackMissingCodeCount;

    void record(AircraftResolution resolution) {
      if (resolution == null) {
        return;
      }
      String codeKey = displayAircraftCode(resolution.normalizedCode());
      observedCounts.merge(codeKey, 1, Integer::sum);
      if (resolution.fallbackToDefault()) {
        fallbackToDefaultCount++;
      }
      if (resolution.missingCode()) {
        fallbackMissingCodeCount++;
      }
      if (resolution.unmappedCode()) {
        unmappedCounts.merge(codeKey, 1, Integer::sum);
      }
    }

    void merge(AircraftResolutionDiagnostics other) {
      if (other == null) {
        return;
      }
      other.observedCounts.forEach((key, value) -> observedCounts.merge(key, value, Integer::sum));
      other.unmappedCounts.forEach((key, value) -> unmappedCounts.merge(key, value, Integer::sum));
      fallbackToDefaultCount += other.fallbackToDefaultCount;
      fallbackMissingCodeCount += other.fallbackMissingCodeCount;
    }

    int observedTotal() {
      return observedCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    int fallbackToDefaultCount() {
      return fallbackToDefaultCount;
    }

    int fallbackMissingCodeCount() {
      return fallbackMissingCodeCount;
    }

    String describeObservedTop() {
      return describeTop(observedCounts);
    }

    String describeUnmappedTop() {
      return describeTop(unmappedCounts);
    }

    private String describeTop(Map<String, Integer> counts) {
      if (counts.isEmpty()) {
        return "none";
      }
      return counts.entrySet().stream()
          .sorted(
              Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                  .thenComparing(Map.Entry.comparingByKey()))
          .limit(MAX_TOP_CODES)
          .map(entry -> entry.getKey() + "=" + entry.getValue())
          .collect(Collectors.joining(", "));
    }

    private static String displayAircraftCode(String code) {
      return StringUtils.hasText(code) ? code : "<blank>";
    }
  }

  public record FlightImportSummary(
      int fetched, int inserted, int updated, int skipped, int failed) {}
}
