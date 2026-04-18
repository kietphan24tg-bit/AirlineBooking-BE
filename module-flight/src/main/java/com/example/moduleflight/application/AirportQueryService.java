package com.example.moduleflight.application;

import com.example.moduleflight.repository.AirportGroupedProjection;
import com.example.moduleflight.repository.AirportRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AirportQueryService {

  private static final String UNKNOWN_COUNTRY = "UNKNOWN";
  private static final List<String> VIETNAM_PRIORITY = List.of("SGN", "HAN", "DAD", "PQC", "CXR");

  private final AirportRepository airportRepository;
  private final AirportMarketService airportMarketService;

  public AirportQueryService(
      AirportRepository airportRepository, AirportMarketService airportMarketService) {
    this.airportRepository = airportRepository;
    this.airportMarketService = airportMarketService;
  }

  public Map<String, List<AirportGroupedItem>> findGroupedByDetectedMarket(
      AirportMarketContext marketContext) {
    List<AirportGroupedProjection> rows = airportRepository.findAllForGroupedByCountry();
    Map<String, CountryGroup> grouped = groupRows(rows);
    String market = airportMarketService.detectMarket(marketContext);
    String preferredCountryName = airportMarketService.countryNameFromMarket(market);
    String preferredKey = findPreferredCountryKey(grouped, market, preferredCountryName);
    if (!StringUtils.hasText(preferredKey)) {
      return toGroupedItems(grouped);
    }

    Map<String, List<AirportGroupedItem>> result = new LinkedHashMap<>();
    CountryGroup preferredGroup = grouped.remove(preferredKey);
    result.put(preferredKey, sortPreferredItems(preferredGroup.items(), market));
    result.putAll(toGroupedItems(grouped));
    return result;
  }

  public Map<String, List<AirportGroupedItem>> findGroupedByCountry(String countryFilter) {
    if (StringUtils.hasText(countryFilter)) {
      String country = countryFilter.trim();
      List<AirportGroupedProjection> rows = airportRepository.findByCountryForGrouped(country);
      Map<String, List<AirportGroupedItem>> result = new LinkedHashMap<>();
      result.put(country, toItems(rows));
      return result;
    }

    List<AirportGroupedProjection> rows = airportRepository.findAllForGroupedByCountry();
    Map<String, List<AirportGroupedItem>> grouped = new LinkedHashMap<>();
    for (AirportGroupedProjection row : rows) {
      String countryKey =
          row.getCountry() == null || row.getCountry().isBlank()
              ? UNKNOWN_COUNTRY
              : row.getCountry().trim();
      grouped.computeIfAbsent(countryKey, key -> new ArrayList<>()).add(toItem(row));
    }
    return grouped;
  }

  private static List<AirportGroupedItem> toItems(List<AirportGroupedProjection> rows) {
    List<AirportGroupedItem> items = new ArrayList<>(rows.size());
    for (AirportGroupedProjection row : rows) {
      items.add(toItem(row));
    }
    return items;
  }

  private static AirportGroupedItem toItem(AirportGroupedProjection row) {
    return new AirportGroupedItem(
        row.getId(), row.getCode(), row.getName(), row.getCity(), row.getCountryCode());
  }

  private Map<String, CountryGroup> groupRows(List<AirportGroupedProjection> rows) {
    Map<String, CountryGroup> grouped = new LinkedHashMap<>();
    for (AirportGroupedProjection row : rows) {
      String countryKey = countryKey(row);
      CountryGroup group = grouped.computeIfAbsent(countryKey, CountryGroup::new);
      if (StringUtils.hasText(row.getCountryCode())) {
        group.countryCodes().add(row.getCountryCode().trim().toUpperCase(Locale.ROOT));
      }
      group.items().add(toItem(row));
    }
    return grouped;
  }

  private Map<String, List<AirportGroupedItem>> toGroupedItems(Map<String, CountryGroup> groups) {
    Map<String, List<AirportGroupedItem>> result = new LinkedHashMap<>();
    groups.forEach((key, group) -> result.put(key, List.copyOf(group.items())));
    return result;
  }

  private String findPreferredCountryKey(
      Map<String, CountryGroup> grouped, String market, String preferredCountryName) {
    String normalizedMarket =
        StringUtils.hasText(market) ? market.trim().toUpperCase(Locale.ROOT) : null;
    if (StringUtils.hasText(normalizedMarket)) {
      for (Map.Entry<String, CountryGroup> entry : grouped.entrySet()) {
        if (entry.getValue().countryCodes().contains(normalizedMarket)) {
          return entry.getKey();
        }
      }
    }
    if (StringUtils.hasText(preferredCountryName)) {
      for (String key : grouped.keySet()) {
        if (key.equalsIgnoreCase(preferredCountryName.trim())) {
          return key;
        }
      }
    }
    return null;
  }

  private List<AirportGroupedItem> sortPreferredItems(
      List<AirportGroupedItem> items, String market) {
    return items.stream().sorted(preferredAirportComparator(market)).toList();
  }

  private Comparator<AirportGroupedItem> preferredAirportComparator(String market) {
    Comparator<String> textComparator = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
    if ("VN".equalsIgnoreCase(market)) {
      return Comparator.comparingInt(this::vietnamPriority)
          .thenComparing(AirportGroupedItem::city, textComparator)
          .thenComparing(AirportGroupedItem::name, textComparator)
          .thenComparing(AirportGroupedItem::code, textComparator);
    }
    return Comparator.comparing(AirportGroupedItem::city, textComparator)
        .thenComparing(AirportGroupedItem::name, textComparator)
        .thenComparing(AirportGroupedItem::code, textComparator);
  }

  private int vietnamPriority(AirportGroupedItem airport) {
    if (!StringUtils.hasText(airport.code())) {
      return VIETNAM_PRIORITY.size();
    }
    int index = VIETNAM_PRIORITY.indexOf(airport.code().toUpperCase(Locale.ROOT));
    return index >= 0 ? index : VIETNAM_PRIORITY.size();
  }

  private static String countryKey(AirportGroupedProjection row) {
    return row.getCountry() == null || row.getCountry().isBlank()
        ? UNKNOWN_COUNTRY
        : row.getCountry().trim();
  }

  private record CountryGroup(
      String country, List<String> countryCodes, List<AirportGroupedItem> items) {

    CountryGroup(String country) {
      this(country, new ArrayList<>(), new ArrayList<>());
    }
  }
}
