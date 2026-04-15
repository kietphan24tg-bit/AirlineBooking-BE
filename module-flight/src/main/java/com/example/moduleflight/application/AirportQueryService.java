package com.example.moduleflight.application;

import com.example.moduleflight.repository.AirportGroupedProjection;
import com.example.moduleflight.repository.AirportRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AirportQueryService {

  private static final String UNKNOWN_COUNTRY = "UNKNOWN";

  private final AirportRepository airportRepository;

  public AirportQueryService(AirportRepository airportRepository) {
    this.airportRepository = airportRepository;
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
    return new AirportGroupedItem(row.getId(), row.getCode(), row.getName(), row.getCity());
  }
}
