package com.example.moduleflight.application;

import com.example.moduleflight.entity.FlightFareEntity;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
// identify the country code and currency of the flight
public class CountryCurrencyResolver {

  private static final String DEFAULT_COUNTRY_CODE = "VN";
  private static final String DEFAULT_CURRENCY = "VND";

  private static final Map<String, String> COUNTRY_ALIASES =
      Map.ofEntries(
          Map.entry("VIETNAM", "VN"),
          Map.entry("VIET_NAM", "VN"),
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
      Map.of(
          "VN", "VND",
          "US", "USD",
          "GB", "GBP",
          "DE", "EUR",
          "JP", "JPY",
          "AU", "AUD",
          "SG", "SGD");

  public String resolveCountryCode(FlightFareEntity fare) {
    if (fare == null
        || fare.getFlight() == null
        || fare.getFlight().getDepartureAirport() == null) {
      return DEFAULT_COUNTRY_CODE;
    }
    String country = fare.getFlight().getDepartureAirport().getCountry();
    if (!StringUtils.hasText(country)) {
      return DEFAULT_COUNTRY_CODE;
    }
    String normalized = country.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    return COUNTRY_ALIASES.getOrDefault(normalized, DEFAULT_COUNTRY_CODE);
  }

  public String resolveCurrency(String countryCode) {
    if (!StringUtils.hasText(countryCode)) {
      return DEFAULT_CURRENCY;
    }
    return CURRENCY_BY_COUNTRY.getOrDefault(
        countryCode.trim().toUpperCase(Locale.ROOT), DEFAULT_CURRENCY);
  }
}
