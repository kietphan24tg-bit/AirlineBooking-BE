package com.example.moduleflight.application;

import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.UserRecentAirportEntity;
import com.example.moduleflight.repository.AirportRepository;
import com.example.moduleflight.repository.UserRecentAirportRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AirportPickerService {

  private static final int DEFAULT_RECENT_LIMIT = 5;
  private static final int DEFAULT_SUGGESTED_LIMIT = 10;
  private static final int DEFAULT_INITIAL_LIMIT = 10;
  private static final int DEFAULT_SEARCH_LIMIT = 10;
  private static final int MAX_SEARCH_LIMIT = 20;
  private static final String FALLBACK_MARKET = "VN";
  private static final List<String> VIETNAM_PRIORITY = List.of("SGN", "HAN", "DAD", "PQC", "CXR");

  private final AirportRepository airportRepository;
  private final UserRecentAirportRepository userRecentAirportRepository;
  private final AirportMarketService airportMarketService;

  public AirportPickerService(
      AirportRepository airportRepository,
      UserRecentAirportRepository userRecentAirportRepository,
      AirportMarketService airportMarketService) {
    this.airportRepository = airportRepository;
    this.userRecentAirportRepository = userRecentAirportRepository;
    this.airportMarketService = airportMarketService;
  }

  @Transactional(readOnly = true)
  public AirportPickerResult picker(
      Long userId,
      String acceptLanguage,
      String timezone,
      String siteLocale,
      String geoIpCountry,
      Integer recentLimit,
      Integer suggestedLimit,
      Integer initialLimit) {
    int effectiveRecentLimit = clamp(recentLimit, 3, 5, DEFAULT_RECENT_LIMIT);
    int effectiveSuggestedLimit = clamp(suggestedLimit, 6, 10, DEFAULT_SUGGESTED_LIMIT);
    int effectiveInitialLimit = clamp(initialLimit, 8, 12, DEFAULT_INITIAL_LIMIT);

    List<AirportPickerItem> recent = loadRecent(userId, effectiveRecentLimit);
    String market =
        airportMarketService.detectMarket(
            new AirportMarketContext(userId, acceptLanguage, timezone, siteLocale, geoIpCountry));
    List<AirportPickerItem> suggested =
        loadSuggested(
            market, effectiveSuggestedLimit, recent.stream().map(AirportPickerItem::id).toList());
    List<AirportPickerInitialItem> initialItems =
        buildInitialItems(recent, suggested, effectiveInitialLimit);

    return new AirportPickerResult(market, recent, suggested, initialItems);
  }

  @Transactional(readOnly = true)
  public List<AirportPickerItem> recent(Long userId, Integer limit) {
    int effectiveLimit = clamp(limit, 1, 10, DEFAULT_RECENT_LIMIT);
    return loadRecent(userId, effectiveLimit);
  }

  @Transactional(readOnly = true)
  public List<AirportPickerItem> search(String query, Integer limit) {
    if (!StringUtils.hasText(query)) {
      return List.of();
    }
    String term = query.trim().toLowerCase(Locale.ROOT);
    int effectiveLimit = clamp(limit, 1, MAX_SEARCH_LIMIT, DEFAULT_SEARCH_LIMIT);
    List<AirportEntity> candidates =
        airportRepository.searchCandidates(term, term + "%", "%" + term + "%");

    LinkedHashMap<Long, AirportPickerItem> unique = new LinkedHashMap<>();
    candidates.stream()
        .filter(airport -> airport.getId() != null)
        .sorted(searchComparator(term))
        .forEach(airport -> unique.putIfAbsent(airport.getId(), toItem(airport)));

    return unique.values().stream().limit(effectiveLimit).toList();
  }

  private List<AirportPickerItem> loadRecent(Long userId, int limit) {
    if (userId == null) {
      return List.of();
    }
    List<UserRecentAirportEntity> rows =
        userRecentAirportRepository.findByUserIdOrderByLastUsedAtDesc(
            userId, PageRequest.of(0, limit));
    LinkedHashMap<Long, AirportPickerItem> unique = new LinkedHashMap<>();
    for (UserRecentAirportEntity row : rows) {
      AirportEntity airport = row.getAirport();
      if (airport != null && airport.getId() != null) {
        unique.putIfAbsent(airport.getId(), toItem(airport));
      }
    }
    return List.copyOf(unique.values());
  }

  private List<AirportPickerItem> loadSuggested(
      String market, int limit, List<Long> recentAirportIds) {
    Set<Long> recentIds = new HashSet<>(recentAirportIds);
    List<AirportEntity> source = findSuggestedAirportSource(market);
    if (source.isEmpty() && !FALLBACK_MARKET.equals(market)) {
      source = findSuggestedAirportSource(FALLBACK_MARKET);
    }
    return filterAndSortSuggested(source, market, recentIds, limit).stream()
        .map(AirportPickerService::toItem)
        .toList();
  }

  private List<AirportEntity> findSuggestedAirportSource(String market) {
    List<AirportEntity> byCountryCode =
        airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode(market);
    if (!byCountryCode.isEmpty()) {
      return byCountryCode;
    }
    String countryName = airportMarketService.countryNameFromMarket(market);
    return StringUtils.hasText(countryName)
        ? airportRepository.findByCountryIgnoreCaseOrderByCityNameCode(countryName)
        : List.of();
  }

  private List<AirportEntity> filterAndSortSuggested(
      List<AirportEntity> source, String market, Set<Long> recentIds, int limit) {
    return source.stream()
        .filter(airport -> airport.getId() != null && !recentIds.contains(airport.getId()))
        .sorted(suggestionComparator(market))
        .limit(limit)
        .toList();
  }

  private Comparator<AirportEntity> suggestionComparator(String market) {
    if ("VN".equals(market)) {
      return Comparator.comparingInt(this::vietnamPriority)
          .thenComparing(AirportEntity::getCity, String.CASE_INSENSITIVE_ORDER)
          .thenComparing(AirportEntity::getName, String.CASE_INSENSITIVE_ORDER)
          .thenComparing(AirportEntity::getCode, String.CASE_INSENSITIVE_ORDER);
    }
    return Comparator.comparing(AirportEntity::getCity, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(AirportEntity::getName, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(AirportEntity::getCode, String.CASE_INSENSITIVE_ORDER);
  }

  private int vietnamPriority(AirportEntity airport) {
    int index = VIETNAM_PRIORITY.indexOf(airport.getCode().toUpperCase(Locale.ROOT));
    return index >= 0 ? index : VIETNAM_PRIORITY.size();
  }

  private Comparator<AirportEntity> searchComparator(String term) {
    Comparator<String> textComparator = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
    return Comparator.comparingInt((AirportEntity airport) -> searchRank(airport, term))
        .thenComparing(AirportEntity::getCity, textComparator)
        .thenComparing(AirportEntity::getName, textComparator)
        .thenComparing(AirportEntity::getCode, textComparator);
  }

  private int searchRank(AirportEntity airport, String term) {
    String code = lower(airport.getCode());
    if (code.equals(term)) {
      return 0;
    }
    if (code.startsWith(term)) {
      return 1;
    }
    if (lower(airport.getCity()).startsWith(term)) {
      return 2;
    }
    if (lower(airport.getName()).contains(term)) {
      return 3;
    }
    if (lower(airport.getCountry()).contains(term)) {
      return 4;
    }
    return 5;
  }

  private String lower(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private List<AirportPickerInitialItem> buildInitialItems(
      List<AirportPickerItem> recent, List<AirportPickerItem> suggested, int limit) {
    List<AirportPickerInitialItem> items = new ArrayList<>(recent.size() + suggested.size());
    recent.forEach(item -> items.add(toInitialItem(item, "RECENT")));
    suggested.forEach(item -> items.add(toInitialItem(item, "SUGGESTED")));
    if (items.size() <= limit) {
      return List.copyOf(items);
    }
    return List.copyOf(items.subList(0, limit));
  }

  private static AirportPickerItem toItem(AirportEntity airport) {
    return new AirportPickerItem(
        airport.getId(),
        airport.getCode(),
        airport.getName(),
        airport.getCity(),
        airport.getCountry(),
        airport.getCountryCode());
  }

  private static AirportPickerInitialItem toInitialItem(AirportPickerItem item, String source) {
    return new AirportPickerInitialItem(
        item.id(),
        item.code(),
        item.name(),
        item.city(),
        item.country(),
        item.countryCode(),
        source);
  }

  private static int clamp(Integer value, int min, int max, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    return Math.max(min, Math.min(max, value));
  }
}
