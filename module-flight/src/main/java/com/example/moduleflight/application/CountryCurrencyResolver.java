package com.example.moduleflight.application;

import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
// identify the country code and currency of the flight
public class CountryCurrencyResolver {

  private static final String DEFAULT_COUNTRY_CODE = "GL";
  private static final String DEFAULT_CURRENCY = "VND";
  private static final Set<String> REGIONAL_COUNTRIES = Set.of("VN", "TH", "MY", "ID", "PH", "SG");

  private static final Map<String, String> COUNTRY_ALIASES =
      Map.ofEntries(
          Map.entry("VIETNAM", "VN"),
          Map.entry("VIET_NAM", "VN"),
          Map.entry("THAILAND", "TH"),
          Map.entry("MALAYSIA", "MY"),
          Map.entry("INDONESIA", "ID"),
          Map.entry("PHILIPPINES", "PH"),
          Map.entry("UNITED_STATES", "US"),
          Map.entry("UNITED_STATES_OF_AMERICA", "US"),
          Map.entry("USA", "US"),
          Map.entry("US", "US"),
          Map.entry("UNITED_KINGDOM", "GB"),
          Map.entry("UK", "GB"),
          Map.entry("GREAT_BRITAIN", "GB"),
          Map.entry("BRITAIN", "GB"),
          Map.entry("GERMANY", "DE"),
          Map.entry("DEUTSCHLAND", "DE"),
          Map.entry("JAPAN", "JP"),
          Map.entry("AUSTRALIA", "AU"),
          Map.entry("SINGAPORE", "SG"));

  private static final Map<String, String> CURRENCY_BY_COUNTRY =
      Map.ofEntries(
          Map.entry("VN", "VND"),
          Map.entry("TH", "THB"),
          Map.entry("MY", "MYR"),
          Map.entry("ID", "IDR"),
          Map.entry("PH", "PHP"),
          Map.entry("US", "USD"),
          Map.entry("GB", "GBP"),
          Map.entry("DE", "EUR"),
          Map.entry("JP", "JPY"),
          Map.entry("AU", "AUD"),
          Map.entry("SG", "SGD"));

  public String resolveCountryCode(FlightFareEntity fare) {
    return resolveCountryCode(fare == null ? null : fare.getFlight());
  }

  public String resolveCountryCode(FlightEntity flight) {
    return resolveCountryCode(flight == null ? null : flight.getDepartureAirport());
  }

  public String resolveCountryCode(AirportEntity airport) {
    if (airport == null) {
      return DEFAULT_COUNTRY_CODE;
    }
    String countryCode = airport.getCountryCode();
    if (StringUtils.hasText(countryCode)) {
      String normalizedCode = countryCode.trim().toUpperCase(Locale.ROOT);
      if (CURRENCY_BY_COUNTRY.containsKey(normalizedCode)) {
        return normalizedCode;
      }
    }
    String country = airport.getCountry();
    if (!StringUtils.hasText(country)) {
      return DEFAULT_COUNTRY_CODE;
    }
    String normalized = country.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    return COUNTRY_ALIASES.getOrDefault(normalized, DEFAULT_COUNTRY_CODE);
  }

  public String resolveDestinationCountryCode(FlightEntity flight) {
    if (flight == null) {
      return DEFAULT_COUNTRY_CODE;
    }
    return resolveCountryCode(flight.getArrivalAirport());
  }

  public String resolveCurrency(String countryCode) {
    // Unknown markets fall back to the global pricing market, which currently settles in VND.
    if (!StringUtils.hasText(countryCode)) {
      return DEFAULT_CURRENCY;
    }
    return CURRENCY_BY_COUNTRY.getOrDefault(
        countryCode.trim().toUpperCase(Locale.ROOT), DEFAULT_CURRENCY);
  }

  public PricingTripScope resolveTripScope(FlightEntity flight) {
    String origin = resolveCountryCode(flight);
    String destination = resolveDestinationCountryCode(flight);
    if (origin.equals(destination)) {
      return PricingTripScope.DOMESTIC;
    }
    if (REGIONAL_COUNTRIES.contains(origin) && REGIONAL_COUNTRIES.contains(destination)) {
      return PricingTripScope.REGIONAL;
    }
    return PricingTripScope.INTERNATIONAL;
  }
}
