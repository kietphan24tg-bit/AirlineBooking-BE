package com.example.moduleflight.application;

import com.example.moduleflight.config.AirlineImportProperties;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.integration.airlabs.AirlabsAirlinesClient;
import com.example.moduleflight.integration.airlabs.dto.AirlabsAirlineItem;
import com.example.moduleflight.repository.AirlineRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AirlineImportService {

  private static final Logger LOG = LoggerFactory.getLogger(AirlineImportService.class);

  private static final String AIRLABS_LOGO_MEDIUM = "https://airlabs.co/img/airline/m/%s.png";

  private final AirlabsAirlinesClient airlabsAirlinesClient;
  private final AirlineRepository airlineRepository;
  private final AirlineImportProperties properties;

  public AirlineImportService(
      AirlabsAirlinesClient airlabsAirlinesClient,
      AirlineRepository airlineRepository,
      AirlineImportProperties properties) {
    this.airlabsAirlinesClient = airlabsAirlinesClient;
    this.airlineRepository = airlineRepository;
    this.properties = properties;
  }

  @Transactional
  public AirlineImportSummary importAirlines() {
    if (!StringUtils.hasText(properties.apiKey())) {
      LOG.warn("Airline import skipped: app.airline-import.api-key is not configured.");
      return new AirlineImportSummary(0, 0, 0, 0, 0);
    }

    List<AirlabsAirlineItem> items = airlabsAirlinesClient.fetchAirlines();
    int fetched = items.size();

    Map<String, String> countryNamesByCode = loadCountryNamesByCode();

    Map<String, AirlineCandidate> byCode = new LinkedHashMap<>();
    int skipped = 0;
    for (AirlabsAirlineItem item : items) {
      var candidate = toCandidate(item, countryNamesByCode);
      if (candidate == null) {
        skipped++;
        continue;
      }
      byCode.putIfAbsent(candidate.code(), candidate);
    }

    UpsertResult upsertResult = upsert(new ArrayList<>(byCode.values()));
    AirlineImportSummary summary =
        new AirlineImportSummary(
            fetched, upsertResult.inserted(), upsertResult.updated(), skipped, 0);
    LOG.info(
        "Airline import finished: fetched={}, inserted={}, updated={}, skipped={}, failed={}",
        summary.fetched(),
        summary.inserted(),
        summary.updated(),
        summary.skipped(),
        summary.failed());
    return summary;
  }

  private Map<String, String> loadCountryNamesByCode() {
    if (!properties.fetchCountriesEnrichment()) {
      return Map.of();
    }
    try {
      Map<String, String> fetched = airlabsAirlinesClient.fetchCountryNamesByCode();
      if (fetched == null || fetched.isEmpty()) {
        LOG.warn(
            "AirLabs countries list was empty; country display names will use Java Locale fallback only.");
        return Map.of();
      }
      LOG.debug("Loaded {} country names from AirLabs Countries DB.", fetched.size());
      return fetched;
    } catch (RuntimeException exception) {
      LOG.warn(
          "AirLabs countries fetch failed; country display names will use Java Locale fallback only: {}",
          exception.getMessage());
      return Map.of();
    }
  }

  AirlineCandidate toCandidate(AirlabsAirlineItem item, Map<String, String> countryNamesByCode) {
    if (item == null) {
      return null;
    }
    String name = item.name() == null ? null : item.name().trim();
    if (!StringUtils.hasText(name)) {
      return null;
    }
    String iata = normalizeCode(item.iataCode());
    String icao = normalizeCode(item.icaoCode());
    if (properties.requireIata() && !StringUtils.hasText(iata)) {
      return null;
    }
    String code = StringUtils.hasText(iata) ? iata : icao;
    if (!StringUtils.hasText(code)) {
      return null;
    }

    String countryCode = normalizeCountryCode(item.countryCode());
    String country = resolveCountryName(countryCode, countryNamesByCode);
    String logoUrl = StringUtils.hasText(iata) ? AIRLABS_LOGO_MEDIUM.formatted(iata) : null;

    return new AirlineCandidate(code, name, logoUrl, countryCode, country, null);
  }

  private UpsertResult upsert(List<AirlineCandidate> candidates) {
    if (candidates.isEmpty()) {
      return new UpsertResult(0, 0);
    }

    Map<String, AirlineCandidate> byCode = new LinkedHashMap<>();
    for (AirlineCandidate candidate : candidates) {
      byCode.put(candidate.code(), candidate);
    }

    List<AirlineEntity> existingList = airlineRepository.findByCodeIn(byCode.keySet());
    Map<String, AirlineEntity> existingByCode = new LinkedHashMap<>();
    for (AirlineEntity existing : existingList) {
      existingByCode.put(existing.getCode(), existing);
    }

    int inserted = 0;
    int updated = 0;
    OffsetDateTime now = OffsetDateTime.now();
    List<AirlineEntity> toSave = new ArrayList<>();

    for (AirlineCandidate candidate : byCode.values()) {
      AirlineEntity existing = existingByCode.get(candidate.code());
      if (existing == null) {
        AirlineEntity created = new AirlineEntity();
        created.setCode(candidate.code());
        created.setName(candidate.name());
        created.setLogoUrl(candidate.logoUrl());
        created.setCountryCode(candidate.countryCode());
        created.setCountry(candidate.country());
        created.setCity(candidate.city());
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        toSave.add(created);
        inserted++;
      } else {
        existing.setName(candidate.name());
        existing.setLogoUrl(candidate.logoUrl());
        existing.setCountryCode(candidate.countryCode());
        existing.setCountry(candidate.country());
        existing.setUpdatedAt(now);
        toSave.add(existing);
        updated++;
      }
    }

    airlineRepository.saveAll(toSave);
    return new UpsertResult(inserted, updated);
  }

  private static String normalizeCode(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return raw.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeCountryCode(String raw) {
    String normalized = normalizeCode(raw);
    if (normalized == null || normalized.length() != 2) {
      return null;
    }
    return normalized;
  }

  private static String resolveCountryName(String iso2, Map<String, String> countryNamesByCode) {
    if (iso2 == null) {
      return null;
    }
    Map<String, String> lookup =
        countryNamesByCode == null ? Collections.emptyMap() : countryNamesByCode;
    String fromCountriesDb = lookup.get(iso2);
    if (StringUtils.hasText(fromCountriesDb)) {
      return fromCountriesDb.trim();
    }
    return displayCountryNameFromLocale(iso2);
  }

  private static String displayCountryNameFromLocale(String iso2) {
    try {
      String name = Locale.of("en", iso2).getDisplayCountry(Locale.ENGLISH);
      return StringUtils.hasText(name) ? name : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  record AirlineCandidate(
      String code, String name, String logoUrl, String countryCode, String country, String city) {}

  private record UpsertResult(int inserted, int updated) {}

  public record AirlineImportSummary(
      int fetched, int inserted, int updated, int skipped, int failed) {}
}
