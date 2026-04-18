package com.example.moduleflight.application;

import com.example.moduleflight.config.AirportMarketProperties;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.UserRecentAirportEntity;
import com.example.moduleflight.repository.UserRecentAirportRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AirportMarketService {

  private static final Logger log = LoggerFactory.getLogger(AirportMarketService.class);

  private static final int[] RECENT_DECAY_SCORES = {90, 40, 20};
  private static final int RECENT_SEARCH_COUNT_BOOST_CAP = 5;
  private static final int RECENT_MARKET_SCORE_CAP = 150;
  private static final int USER_PROFILE_SCORE = 75;
  private static final int ACCEPT_LANGUAGE_REGION_SCORE = 25;
  private static final int TIMEZONE_SCORE = 40;
  private static final int ACCEPT_LANGUAGE_ONLY_SCORE = 35;
  private static final int SITE_LOCALE_SCORE = 55;
  private static final int DEFAULT_SCORE = 1;
  private static final int RECENT_MARKET_LIMIT = 8;
  private static final Map<String, String> LANGUAGE_MARKETS =
      Map.ofEntries(
          Map.entry("vi", "VN"),
          Map.entry("ja", "JP"),
          Map.entry("ko", "KR"),
          Map.entry("th", "TH"));
  private static final Map<AirportMarketSignalSource, Integer> SOURCE_PRIORITY = sourcePriority();
  private static final Set<String> ISO_COUNTRIES =
      Set.copyOf(Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2));

  private final UserRecentAirportRepository userRecentAirportRepository;
  private final AirportMarketProperties properties;
  private final List<UserMarketProfileProvider> userMarketProfileProviders;
  private final Map<String, String> timezoneMarkets;

  public AirportMarketService(
      UserRecentAirportRepository userRecentAirportRepository,
      AirportMarketProperties properties,
      List<UserMarketProfileProvider> userMarketProfileProviders) {
    this.userRecentAirportRepository = userRecentAirportRepository;
    this.properties = properties;
    this.userMarketProfileProviders =
        userMarketProfileProviders == null ? List.of() : List.copyOf(userMarketProfileProviders);
    this.timezoneMarkets = loadTimezoneMarkets();
  }

  public String detectMarket(AirportMarketContext context) {
    AirportMarketContext safeContext = safeContext(context);
    List<AirportMarketSignal> signals = buildSignals(safeContext);
    String market = strongestMarket(signals);
    logDetection(safeContext, signals, market);
    return market;
  }

  public List<AirportMarketSignal> buildSignals(AirportMarketContext context) {
    AirportMarketContext safeContext = safeContext(context);
    List<AirportMarketSignal> signals = new ArrayList<>();
    signals.addAll(recentSignals(safeContext.userId()));
    userProfileMarket(safeContext.userId())
        .ifPresent(
            market ->
                signals.add(
                    new AirportMarketSignal(
                        market, AirportMarketSignalSource.USER_PROFILE, USER_PROFILE_SCORE)));
    normalizeMarket(safeContext.geoIpCountry())
        .ifPresent(
            market ->
                signals.add(
                    new AirportMarketSignal(
                        market, AirportMarketSignalSource.GEOIP, properties.geoipScore())));
    signals.addAll(acceptLanguageSignals(safeContext.acceptLanguage()));
    marketFromTimezone(safeContext.timezone())
        .ifPresent(
            market ->
                signals.add(
                    new AirportMarketSignal(
                        market, AirportMarketSignalSource.TIMEZONE, TIMEZONE_SCORE)));
    siteLocaleMarket(safeContext.siteLocale())
        .ifPresent(
            market ->
                signals.add(
                    new AirportMarketSignal(
                        market, AirportMarketSignalSource.SITE_LOCALE, SITE_LOCALE_SCORE)));
    normalizeMarket(properties.defaultMarket())
        .ifPresent(
            market ->
                signals.add(
                    new AirportMarketSignal(
                        market, AirportMarketSignalSource.DEFAULT, DEFAULT_SCORE)));
    return signals;
  }

  private AirportMarketContext safeContext(AirportMarketContext context) {
    return context == null ? new AirportMarketContext(null, null, null, null, null) : context;
  }

  private void logDetection(
      AirportMarketContext context, List<AirportMarketSignal> signals, String market) {
    if (!log.isDebugEnabled()) {
      return;
    }
    log.debug(
        "Airport market detection inputs: userPresent={}, acceptLanguage={}, timezone={},"
            + " siteLocale={}, geoIpCountry={}",
        context.userId() != null,
        context.acceptLanguage(),
        context.timezone(),
        context.siteLocale(),
        context.geoIpCountry());
    log.debug("Airport market detection signals: {}", signals);
    log.debug("Airport market detection result: {}", market);
  }

  public String countryNameFromMarket(String market) {
    Optional<String> normalized = normalizeMarket(market);
    if (normalized.isEmpty()) {
      return null;
    }
    String countryName = Locale.of("", normalized.get()).getDisplayCountry(Locale.ENGLISH);
    return StringUtils.hasText(countryName) ? countryName : null;
  }

  private List<AirportMarketSignal> recentSignals(Long userId) {
    if (userId == null) {
      return List.of();
    }
    List<UserRecentAirportEntity> recentRows =
        userRecentAirportRepository.findByUserIdAndUsageTypeInOrderByLastUsedAtDesc(
            userId,
            List.of(AirportUsageType.DEPARTURE, AirportUsageType.ARRIVAL),
            PageRequest.of(0, RECENT_MARKET_LIMIT));
    Map<String, Integer> scores = new LinkedHashMap<>();
    Map<String, Integer> occurrenceCounts = new LinkedHashMap<>();
    for (UserRecentAirportEntity row : recentRows) {
      marketFromAirport(row.getAirport())
          .ifPresent(
              market -> {
                int occurrence = occurrenceCounts.getOrDefault(market, 0);
                occurrenceCounts.put(market, occurrence + 1);
                if (occurrence >= RECENT_DECAY_SCORES.length) {
                  return;
                }
                int score =
                    RECENT_DECAY_SCORES[occurrence]
                        + Math.max(
                            0, Math.min(row.getSearchCount(), RECENT_SEARCH_COUNT_BOOST_CAP));
                scores.merge(
                    market,
                    score,
                    (currentScore, addedScore) ->
                        Math.min(currentScore + addedScore, RECENT_MARKET_SCORE_CAP));
              });
    }
    return scores.entrySet().stream()
        .map(
            entry ->
                new AirportMarketSignal(
                    entry.getKey(), AirportMarketSignalSource.RECENT_BEHAVIOR, entry.getValue()))
        .toList();
  }

  private Optional<String> userProfileMarket(Long userId) {
    if (userId == null) {
      return Optional.empty();
    }
    return userMarketProfileProviders.stream()
        .map(provider -> provider.findByUserId(userId))
        .flatMap(Optional::stream)
        .map(UserMarketProfile::nationality)
        .map(this::normalizeMarket)
        .flatMap(Optional::stream)
        .findFirst();
  }

  private List<AirportMarketSignal> acceptLanguageSignals(String acceptLanguage) {
    if (!StringUtils.hasText(acceptLanguage)) {
      return List.of();
    }
    List<AirportMarketSignal> signals = new ArrayList<>();
    String[] ranges = acceptLanguage.split(",");
    for (String range : ranges) {
      String token = range.split(";")[0].trim();
      boolean hasRegion = token.contains("-") || token.contains("_");
      marketFromLocaleToken(token, true)
          .ifPresent(
              market ->
                  signals.add(
                      new AirportMarketSignal(
                          market,
                          hasRegion
                              ? AirportMarketSignalSource.ACCEPT_LANGUAGE_REGION
                              : AirportMarketSignalSource.ACCEPT_LANGUAGE_ONLY,
                          hasRegion ? ACCEPT_LANGUAGE_REGION_SCORE : ACCEPT_LANGUAGE_ONLY_SCORE)));
    }
    return signals;
  }

  private Optional<String> siteLocaleMarket(String siteLocale) {
    return marketFromLocaleToken(siteLocale, false);
  }

  private Optional<String> marketFromLocaleToken(String raw, boolean allowLanguageOnly) {
    if (!StringUtils.hasText(raw)) {
      return Optional.empty();
    }
    String normalized = raw.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    Optional<String> directMarket = normalizeMarket(normalized);
    if (directMarket.isPresent()) {
      return directMarket;
    }
    int dash = normalized.indexOf('-');
    if (dash > 0) {
      Optional<String> regionMarket = normalizeMarket(normalized.substring(dash + 1));
      if (regionMarket.isPresent()) {
        return regionMarket;
      }
    }
    String exact = LANGUAGE_MARKETS.get(normalized);
    if (StringUtils.hasText(exact)) {
      return Optional.of(exact);
    }
    if (allowLanguageOnly && dash < 0) {
      return Optional.ofNullable(LANGUAGE_MARKETS.get(normalized));
    }
    return Optional.empty();
  }

  private Optional<String> marketFromTimezone(String timezone) {
    if (!StringUtils.hasText(timezone)) {
      return Optional.empty();
    }
    return Optional.ofNullable(timezoneMarkets.get(timezone.trim()));
  }

  private Optional<String> marketFromAirport(AirportEntity airport) {
    if (airport == null) {
      return Optional.empty();
    }
    Optional<String> countryCode = normalizeMarket(airport.getCountryCode());
    if (countryCode.isPresent()) {
      return countryCode;
    }
    return marketFromCountryName(airport.getCountry());
  }

  private Optional<String> marketFromCountryName(String country) {
    if (!StringUtils.hasText(country)) {
      return Optional.empty();
    }
    for (String isoCountry : ISO_COUNTRIES) {
      String displayName = Locale.of("", isoCountry).getDisplayCountry(Locale.ENGLISH);
      if (displayName.equalsIgnoreCase(country.trim())) {
        return Optional.of(isoCountry);
      }
    }
    return Optional.empty();
  }

  private Optional<String> normalizeMarket(String raw) {
    if (!StringUtils.hasText(raw)) {
      return Optional.empty();
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    if (normalized.length() != 2 || "XX".equals(normalized)) {
      return Optional.empty();
    }
    if (ISO_COUNTRIES.contains(normalized)) {
      return Optional.of(normalized);
    }
    return Optional.empty();
  }

  private String strongestMarket(List<AirportMarketSignal> signals) {
    Map<String, MarketScore> scores = new LinkedHashMap<>();
    for (AirportMarketSignal signal : signals) {
      scores.computeIfAbsent(signal.market(), ignored -> new MarketScore()).add(signal);
    }
    return scores.entrySet().stream()
        .max(
            (left, right) -> {
              int byScore = Integer.compare(left.getValue().score(), right.getValue().score());
              if (byScore != 0) {
                return byScore;
              }
              return Integer.compare(
                  right.getValue().bestPriority(), left.getValue().bestPriority());
            })
        .map(Map.Entry::getKey)
        .orElse(properties.defaultMarket());
  }

  private static Map<AirportMarketSignalSource, Integer> sourcePriority() {
    Map<AirportMarketSignalSource, Integer> priority =
        new EnumMap<>(AirportMarketSignalSource.class);
    priority.put(AirportMarketSignalSource.RECENT_BEHAVIOR, 0);
    priority.put(AirportMarketSignalSource.USER_PROFILE, 1);
    priority.put(AirportMarketSignalSource.GEOIP, 2);
    priority.put(AirportMarketSignalSource.ACCEPT_LANGUAGE_REGION, 3);
    priority.put(AirportMarketSignalSource.TIMEZONE, 4);
    priority.put(AirportMarketSignalSource.ACCEPT_LANGUAGE_ONLY, 5);
    priority.put(AirportMarketSignalSource.SITE_LOCALE, 6);
    priority.put(AirportMarketSignalSource.DEFAULT, 7);
    return Map.copyOf(priority);
  }

  private Map<String, String> loadTimezoneMarkets() {
    Properties properties = new Properties();
    try (InputStream inputStream =
        AirportMarketService.class
            .getClassLoader()
            .getResourceAsStream("airport-picker-timezones.properties")) {
      if (inputStream != null) {
        properties.load(inputStream);
      }
    } catch (IOException ignored) {
      return Map.of();
    }
    Map<String, String> loaded = new LinkedHashMap<>();
    for (String name : properties.stringPropertyNames()) {
      normalizeMarket(properties.getProperty(name))
          .ifPresent(
              market -> {
                if (StringUtils.hasText(name)) {
                  loaded.put(name.trim(), market);
                }
              });
    }
    return Map.copyOf(loaded);
  }

  private static class MarketScore {

    private int score;
    private int bestPriority = Integer.MAX_VALUE;

    void add(AirportMarketSignal signal) {
      this.score += signal.score();
      this.bestPriority = Math.min(bestPriority, SOURCE_PRIORITY.get(signal.source()));
    }

    int score() {
      return score;
    }

    int bestPriority() {
      return bestPriority;
    }
  }
}
