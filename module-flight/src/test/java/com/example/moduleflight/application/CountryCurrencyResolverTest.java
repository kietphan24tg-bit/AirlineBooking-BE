package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import org.junit.jupiter.api.Test;

class CountryCurrencyResolverTest {

  private final CountryCurrencyResolver resolver = new CountryCurrencyResolver();

  @Test
  void resolveCountryCode_prefersSupportedDepartureCountryCode() {
    assertEquals("TH", resolver.resolveCountryCode(flight("TH", "Thailand")));
    assertEquals("MY", resolver.resolveCountryCode(flight("MY", "Malaysia")));
    assertEquals("ID", resolver.resolveCountryCode(flight("ID", "Indonesia")));
    assertEquals("PH", resolver.resolveCountryCode(flight("PH", "Philippines")));
  }

  @Test
  void resolveCountryCode_fallsBackToCountryAliasesForSupportedMarkets() {
    assertEquals("TH", resolver.resolveCountryCode(flight(null, "Thailand")));
    assertEquals("MY", resolver.resolveCountryCode(flight(null, "Malaysia")));
    assertEquals("ID", resolver.resolveCountryCode(flight(null, "Indonesia")));
    assertEquals("PH", resolver.resolveCountryCode(flight(null, "Philippines")));
  }

  @Test
  void resolveCurrency_returnsLocalCurrencyForSeaMarkets() {
    assertEquals("THB", resolver.resolveCurrency("TH"));
    assertEquals("MYR", resolver.resolveCurrency("MY"));
    assertEquals("IDR", resolver.resolveCurrency("ID"));
    assertEquals("PHP", resolver.resolveCurrency("PH"));
  }

  @Test
  void resolveCountryCode_unknownCountryFallsBackToGlobalMarket() {
    assertEquals("GL", resolver.resolveCountryCode(flight("ZZ", "Atlantis")));
    assertEquals("GL", resolver.resolveCountryCode(flight(null, "Atlantis")));
    assertEquals("GL", resolver.resolveCountryCode(null));
  }

  @Test
  void resolveCurrency_globalMarketFallsBackToVnd() {
    assertEquals("VND", resolver.resolveCurrency("GL"));
    assertEquals("VND", resolver.resolveCurrency("ZZ"));
    assertEquals("VND", resolver.resolveCurrency(null));
  }

  private static FlightEntity flight(String countryCode, String country) {
    AirportEntity airport = new AirportEntity();
    airport.setCountryCode(countryCode);
    airport.setCountry(country);

    FlightEntity flight = new FlightEntity();
    flight.setDepartureAirport(airport);
    return flight;
  }
}
