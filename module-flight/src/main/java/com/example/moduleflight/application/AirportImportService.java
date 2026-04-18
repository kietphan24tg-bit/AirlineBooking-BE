package com.example.moduleflight.application;

import com.example.moduleflight.config.AirportImportProperties;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.integration.airportsapi.AirportsApiClient;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.AirportAttributes;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.AirportData;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.IncludedResource;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.RelationshipData;
import com.example.moduleflight.repository.AirportRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Service
public class AirportImportService {

  private static final Logger LOG = LoggerFactory.getLogger(AirportImportService.class);

  private final AirportsApiClient airportsApiClient;
  private final AirportRepository airportRepository;
  private final AirportImportProperties properties;
  private final TransactionTemplate transactionTemplate;

  public AirportImportService(
      AirportsApiClient airportsApiClient,
      AirportRepository airportRepository,
      AirportImportProperties properties,
      PlatformTransactionManager transactionManager) {
    this.airportsApiClient = airportsApiClient;
    this.airportRepository = airportRepository;
    this.properties = properties;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public AirportImportSummary importAirports() {

    String cursor = null;
    Set<String> seenCursors = new HashSet<>();
    int fetched = 0;
    int inserted = 0;
    int updated = 0;
    int skipped = 0;
    int failed = 0;
    Map<SkipReason, Integer> skipBreakdown = new EnumMap<>(SkipReason.class);
    int fallbackCountryCount = 0;
    int fallbackCityCount = 0;
    int fallbackTimeZoneCount = 0;

    while (true) {
      if (!seenCursors.add(cursor)) {
        LOG.warn(
            "Airport import detected repeated cursor '{}'; stopping to prevent infinite loop.",
            cursor);
        break;
      }

      AirportsApiPageResponse page = airportsApiClient.fetchPage(cursor);
      List<AirportData> data = page.data() == null ? List.of() : page.data();
      fetched += data.size();

      TransformResult transformResult = transform(page);
      skipped += transformResult.skipped();
      fallbackCountryCount += transformResult.fallbackCountryCount();
      fallbackCityCount += transformResult.fallbackCityCount();
      fallbackTimeZoneCount += transformResult.fallbackTimeZoneCount();
      mergeSkipBreakdown(skipBreakdown, transformResult.skipBreakdown());

      try {
        UpsertResult upsertResult =
            transactionTemplate.execute(status -> upsert(transformResult.candidates()));
        if (upsertResult == null) {
          failed += transformResult.candidates().size();
          LOG.warn("Airport import upsert returned null result for cursor={}", cursor);
        } else {
          inserted += upsertResult.inserted();
          updated += upsertResult.updated();
        }
      } catch (RuntimeException exception) {
        failed += transformResult.candidates().size();
        LOG.warn(
            "Airport import failed on page cursor={}: {}",
            cursor,
            exception.getMessage(),
            exception);
      }

      String nextCursor = resolveNextCursor(page);
      if (nextCursor == null || nextCursor.isBlank()) {
        break;
      }
      if (seenCursors.contains(nextCursor)) {
        LOG.warn(
            "Airport import next cursor '{}' was already processed (current='{}'); stopping to prevent infinite loop.",
            nextCursor,
            cursor);
        break;
      }
      cursor = nextCursor;
    }

    AirportImportSummary summary =
        new AirportImportSummary(fetched, inserted, updated, skipped, failed);
    LOG.info(
        "Airport import finished: fetched={}, inserted={}, updated={}, skipped={}, failed={}, "
            + "fallbackCountry={}, fallbackCity={}, fallbackTimeZone={}",
        summary.fetched(),
        summary.inserted(),
        summary.updated(),
        summary.skipped(),
        summary.failed(),
        fallbackCountryCount,
        fallbackCityCount,
        fallbackTimeZoneCount);
    LOG.info("Airport import skip breakdown: {}", skipBreakdown);
    return summary;
  }

  String resolveNextCursor(AirportsApiPageResponse page) {
    if (page.meta() != null
        && page.meta().nextCursor() != null
        && !page.meta().nextCursor().isBlank()) {
      return page.meta().nextCursor();
    }
    if (page.links() == null || page.links().next() == null || page.links().next().isBlank()) {
      return null;
    }

    String cursorFromParams =
        UriComponentsBuilder.fromUriString(page.links().next())
            .build()
            .getQueryParams()
            .getFirst("page[cursor]");
    if (cursorFromParams != null && !cursorFromParams.isBlank()) {
      return cursorFromParams;
    }
    int separator = page.links().next().indexOf("page%5Bcursor%5D=");
    if (separator < 0) {
      return null;
    }
    String encodedCursor = page.links().next().substring(separator + "page%5Bcursor%5D=".length());
    int nextParam = encodedCursor.indexOf('&');
    String finalEncodedCursor =
        nextParam >= 0 ? encodedCursor.substring(0, nextParam) : encodedCursor;
    return UriUtils.decode(finalEncodedCursor, java.nio.charset.StandardCharsets.UTF_8);
  }

  TransformResult transform(AirportsApiPageResponse page) {
    List<AirportData> data = page.data() == null ? List.of() : page.data();
    Map<String, String> countryNamesById = resolveCountryNamesById(page);
    Map<String, String> countryCodesById = resolveCountryCodesById(page);
    Map<String, String> regionNamesById = resolveRegionNamesById(page);
    Set<String> allowedTypes = parseAllowedTypes();

    List<AirportCandidate> candidates = new ArrayList<>();
    int skipped = 0;
    int fallbackCountryCount = 0;
    int fallbackCityCount = 0;
    int fallbackTimeZoneCount = 0;
    Map<SkipReason, Integer> skipBreakdown = new EnumMap<>(SkipReason.class);

    for (AirportData airportData : data) {
      Optional<AirportCandidate> candidate =
          toCandidate(
              airportData, countryNamesById, countryCodesById, regionNamesById, allowedTypes);
      if (candidate.isEmpty()) {
        skipped++;
        recordSkip(skipBreakdown, resolveSkipReason(airportData, allowedTypes));
        continue;
      }
      AirportCandidate finalCandidate = candidate.get();
      if (finalCandidate.usedFallbackCountry()) {
        fallbackCountryCount++;
      }
      if (finalCandidate.usedFallbackCity()) {
        fallbackCityCount++;
      }
      if (finalCandidate.usedFallbackTimeZone()) {
        fallbackTimeZoneCount++;
      } else {
        // no-op
      }
      candidates.add(finalCandidate);
    }
    return new TransformResult(
        candidates,
        skipped,
        fallbackCountryCount,
        fallbackCityCount,
        fallbackTimeZoneCount,
        skipBreakdown);
  }

  Optional<AirportCandidate> toCandidate(
      AirportData airportData,
      Map<String, String> countryNamesById,
      Map<String, String> countryCodesById,
      Map<String, String> regionNamesById,
      Set<String> allowedTypes) {
    AirportAttributes attributes = airportData == null ? null : airportData.attributes();
    if (attributes == null) {
      return Optional.empty();
    }
    String code = normalizeCode(attributes);
    if (!isTypeAllowed(attributes.type(), allowedTypes)) {
      return Optional.empty();
    }
    if (properties.requireIata() && isBlank(attributes.iataCode())) {
      return Optional.empty();
    }
    if (isBlank(code) || isBlank(attributes.name())) {
      return Optional.empty();
    }

    String relationshipCountry = resolveCountryNameFromRelationship(airportData, countryNamesById);
    String relationshipCountryCode =
        resolveCountryCodeFromRelationship(airportData, countryCodesById);
    String relationshipRegion = resolveRegionNameFromRelationship(airportData, regionNamesById);
    ValueWithFallback countryValue =
        resolveCountry(attributes.country(), relationshipCountry, properties.fallbackCountry());
    ValueWithFallback cityValue =
        resolveCity(attributes.city(), relationshipRegion, properties.fallbackCity());
    ValueWithFallback timeZoneValue =
        resolveTimeZone(attributes.timeZone(), properties.fallbackTimeZone());

    return Optional.of(
        new AirportCandidate(
            code,
            attributes.name().trim(),
            cityValue.value(),
            countryValue.value(),
            normalizeCountryCode(relationshipCountryCode),
            timeZoneValue.value(),
            cityValue.usedFallback(),
            countryValue.usedFallback(),
            timeZoneValue.usedFallback()));
  }

  private UpsertResult upsert(List<AirportCandidate> candidates) {
    if (candidates.isEmpty()) {
      return new UpsertResult(0, 0);
    }

    Map<String, AirportCandidate> byCode = new LinkedHashMap<>();
    for (AirportCandidate candidate : candidates) {
      byCode.put(candidate.code(), candidate);
    }

    List<AirportEntity> existingAirports = airportRepository.findByCodeIn(byCode.keySet());
    Map<String, AirportEntity> existingByCode = new LinkedHashMap<>();
    for (AirportEntity existing : existingAirports) {
      existingByCode.put(existing.getCode(), existing);
    }

    int inserted = 0;
    int updated = 0;
    OffsetDateTime now = OffsetDateTime.now();
    List<AirportEntity> toSave = new ArrayList<>();

    for (AirportCandidate candidate : byCode.values()) {
      AirportEntity existing = existingByCode.get(candidate.code());
      if (existing == null) {
        AirportEntity created = new AirportEntity();
        created.setCode(candidate.code());
        created.setName(candidate.name());
        created.setCity(candidate.city());
        created.setCountry(candidate.country());
        created.setCountryCode(candidate.countryCode());
        created.setTimeZone(candidate.timeZone());
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        toSave.add(created);
        inserted++;
      } else {
        existing.setName(candidate.name());
        existing.setCity(
            pickPreferableValue(candidate.city(), existing.getCity(), properties.fallbackCity()));
        existing.setCountry(
            pickPreferableValue(
                candidate.country(), existing.getCountry(), properties.fallbackCountry()));
        existing.setCountryCode(
            pickPreferableCountryCode(candidate.countryCode(), existing.getCountryCode()));
        existing.setTimeZone(
            pickPreferableValue(
                candidate.timeZone(), existing.getTimeZone(), properties.fallbackTimeZone()));
        existing.setUpdatedAt(now);
        toSave.add(existing);
        updated++;
      }
    }

    airportRepository.saveAll(toSave);
    return new UpsertResult(inserted, updated);
  }

  // helper methods
  private String normalizeCode(AirportAttributes attributes) {
    String selectedCode =
        firstNotBlank(attributes.iataCode(), attributes.icaoCode(), attributes.code());
    if (selectedCode == null) {
      return null;
    }
    return selectedCode.trim().toUpperCase(Locale.ROOT);
  }

  private String fallbackIfBlank(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private String pickPreferableValue(String candidate, String existing, String fallbackValue) {
    if (isBlank(existing)) {
      return candidate;
    }
    if (equalsIgnoreCaseTrim(candidate, fallbackValue)
        && !equalsIgnoreCaseTrim(existing, fallbackValue)) {
      return existing;
    }
    return candidate;
  }

  private String pickPreferableCountryCode(String candidate, String existing) {
    if (!isBlank(candidate)) {
      return candidate;
    }
    return existing;
  }

  private boolean equalsIgnoreCaseTrim(String first, String second) {
    if (first == null && second == null) {
      return true;
    }
    if (first == null || second == null) {
      return false;
    }
    return first.trim().equalsIgnoreCase(second.trim());
  }

  private Map<String, String> resolveCountryNamesById(AirportsApiPageResponse page) {
    Map<String, String> byId = new HashMap<>();
    if (page.included() == null) {
      return byId;
    }
    for (IncludedResource includedResource : page.included()) {
      if (includedResource == null || isBlank(includedResource.id())) {
        continue;
      }
      if ("countries".equals(includedResource.type())
          && includedResource.attributes() != null
          && !isBlank(includedResource.attributes().name())) {
        byId.put(includedResource.id(), includedResource.attributes().name().trim());
      }
    }
    return byId;
  }

  private Map<String, String> resolveCountryCodesById(AirportsApiPageResponse page) {
    Map<String, String> byId = new HashMap<>();
    if (page.included() == null) {
      return byId;
    }
    for (IncludedResource includedResource : page.included()) {
      if (includedResource == null || isBlank(includedResource.id())) {
        continue;
      }
      if ("countries".equals(includedResource.type())
          && includedResource.attributes() != null
          && !isBlank(includedResource.attributes().code())) {
        String countryCode = normalizeCountryCode(includedResource.attributes().code());
        if (!isBlank(countryCode)) {
          byId.put(includedResource.id(), countryCode);
        }
      }
    }
    return byId;
  }

  private Map<String, String> resolveRegionNamesById(AirportsApiPageResponse page) {
    Map<String, String> byId = new HashMap<>();
    if (page.included() == null) {
      return byId;
    }
    for (IncludedResource includedResource : page.included()) {
      if (includedResource == null || isBlank(includedResource.id())) {
        continue;
      }
      if ("regions".equals(includedResource.type())
          && includedResource.attributes() != null
          && !isBlank(includedResource.attributes().name())) {
        byId.put(includedResource.id(), includedResource.attributes().name().trim());
      }
    }
    return byId;
  }

  private String resolveCountryNameFromRelationship(
      AirportData airportData, Map<String, String> countryNamesById) {
    RelationshipData relationshipData =
        airportData.relationships() == null || airportData.relationships().country() == null
            ? null
            : airportData.relationships().country().data();
    if (relationshipData == null || isBlank(relationshipData.id())) {
      return null;
    }
    return countryNamesById.get(relationshipData.id());
  }

  private String resolveCountryCodeFromRelationship(
      AirportData airportData, Map<String, String> countryCodesById) {
    RelationshipData relationshipData =
        airportData.relationships() == null || airportData.relationships().country() == null
            ? null
            : airportData.relationships().country().data();
    if (relationshipData == null || isBlank(relationshipData.id())) {
      return null;
    }
    return countryCodesById.get(relationshipData.id());
  }

  private String resolveRegionNameFromRelationship(
      AirportData airportData, Map<String, String> regionNamesById) {
    RelationshipData relationshipData =
        airportData.relationships() == null || airportData.relationships().region() == null
            ? null
            : airportData.relationships().region().data();
    if (relationshipData == null || isBlank(relationshipData.id())) {
      return null;
    }
    return regionNamesById.get(relationshipData.id());
  }

  private ValueWithFallback resolveCountry(
      String directCountry, String relationshipCountry, String fallback) {
    if (!isBlank(relationshipCountry)) {
      return new ValueWithFallback(relationshipCountry.trim(), false);
    }
    if (!isBlank(directCountry)) {
      return new ValueWithFallback(directCountry.trim(), false);
    }
    return new ValueWithFallback(fallback, true);
  }

  private ValueWithFallback resolveCity(
      String directCity, String relationshipRegion, String fallback) {
    if (!isBlank(directCity)) {
      return new ValueWithFallback(directCity.trim(), false);
    }
    if (!isBlank(relationshipRegion)) {
      return new ValueWithFallback(relationshipRegion.trim(), false);
    }
    return new ValueWithFallback(fallback, true);
  }

  private ValueWithFallback resolveTimeZone(String directTimeZone, String fallback) {
    if (!isBlank(directTimeZone)) {
      return new ValueWithFallback(directTimeZone.trim(), false);
    }
    return new ValueWithFallback(fallback, true);
  }

  private Set<String> parseAllowedTypes() {
    if (isBlank(properties.allowedTypes())) {
      return Set.of();
    }
    Set<String> normalizedTypes = new HashSet<>();
    Arrays.stream(properties.allowedTypes().split(","))
        .map(value -> value == null ? null : value.trim().toLowerCase(Locale.ROOT))
        .filter(value -> value != null && !value.isBlank())
        .forEach(normalizedTypes::add);
    return normalizedTypes;
  }

  private boolean isTypeAllowed(String type, Set<String> allowedTypes) {
    if (allowedTypes.isEmpty()) {
      return true;
    }
    if (isBlank(type)) {
      return false;
    }
    return allowedTypes.contains(type.trim().toLowerCase(Locale.ROOT));
  }

  private SkipReason resolveSkipReason(AirportData airportData, Set<String> allowedTypes) {
    if (airportData == null || airportData.attributes() == null) {
      return SkipReason.MISSING_ATTRIBUTES;
    }
    AirportAttributes attributes = airportData.attributes();
    if (!isTypeAllowed(attributes.type(), allowedTypes)) {
      return SkipReason.FILTERED_TYPE;
    }
    if (properties.requireIata() && isBlank(attributes.iataCode())) {
      return SkipReason.MISSING_IATA;
    }
    if (isBlank(normalizeCode(attributes))) {
      return SkipReason.MISSING_CODE;
    }
    if (isBlank(attributes.name())) {
      return SkipReason.MISSING_NAME;
    }
    return SkipReason.UNKNOWN;
  }

  private void recordSkip(Map<SkipReason, Integer> skipBreakdown, SkipReason reason) {
    skipBreakdown.merge(reason, 1, Integer::sum);
  }

  private void mergeSkipBreakdown(
      Map<SkipReason, Integer> total, Map<SkipReason, Integer> current) {
    for (Map.Entry<SkipReason, Integer> entry : current.entrySet()) {
      total.merge(entry.getKey(), entry.getValue(), Integer::sum);
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String normalizeCountryCode(String raw) {
    if (isBlank(raw)) {
      return null;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    return normalized.length() == 2 ? normalized : null;
  }

  private String firstNotBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  record AirportCandidate(
      String code,
      String name,
      String city,
      String country,
      String countryCode,
      String timeZone,
      boolean usedFallbackCity,
      boolean usedFallbackCountry,
      boolean usedFallbackTimeZone) {}

  record TransformResult(
      List<AirportCandidate> candidates,
      int skipped,
      int fallbackCountryCount,
      int fallbackCityCount,
      int fallbackTimeZoneCount,
      Map<SkipReason, Integer> skipBreakdown) {}

  record UpsertResult(int inserted, int updated) {}

  record ValueWithFallback(String value, boolean usedFallback) {}

  enum SkipReason {
    MISSING_ATTRIBUTES,
    FILTERED_TYPE,
    MISSING_IATA,
    MISSING_CODE,
    MISSING_NAME,
    UNKNOWN
  }

  public record AirportImportSummary(
      int fetched, int inserted, int updated, int skipped, int failed) {}
}
