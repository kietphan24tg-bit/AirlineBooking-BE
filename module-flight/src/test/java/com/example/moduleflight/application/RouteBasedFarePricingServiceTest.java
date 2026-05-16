package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FareClassMultiplierEntity;
import com.example.moduleflight.entity.FareClassPriceEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.MarketBasePriceEntity;
import com.example.moduleflight.entity.PricingMarketProfileEntity;
import com.example.moduleflight.entity.RouteBasePriceEntity;
import com.example.moduleflight.entity.TaxFeeEntity;
import com.example.moduleflight.repository.FareClassMultiplierRepository;
import com.example.moduleflight.repository.FareClassPriceRepository;
import com.example.moduleflight.repository.MarketBasePriceRepository;
import com.example.moduleflight.repository.PricingMarketProfileRepository;
import com.example.moduleflight.repository.RouteBasePriceRepository;
import com.example.moduleflight.repository.TaxFeeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RouteBasedFarePricingServiceTest {

  private final RouteBasePriceRepository routeBasePriceRepository = mock(RouteBasePriceRepository.class);
  private final MarketBasePriceRepository marketBasePriceRepository =
      mock(MarketBasePriceRepository.class);
  private final FareClassMultiplierRepository fareClassMultiplierRepository =
      mock(FareClassMultiplierRepository.class);
  private final PricingMarketProfileRepository pricingMarketProfileRepository =
      mock(PricingMarketProfileRepository.class);
  private final FareClassPriceRepository fareClassPriceRepository = mock(FareClassPriceRepository.class);
  private final TaxFeeRepository taxFeeRepository = mock(TaxFeeRepository.class);
  private final CountryCurrencyResolver countryCurrencyResolver = new CountryCurrencyResolver();
  private final RouteBasedFarePricingService service =
      new RouteBasedFarePricingService(
          routeBasePriceRepository,
          marketBasePriceRepository,
          fareClassMultiplierRepository,
          pricingMarketProfileRepository,
          fareClassPriceRepository,
          taxFeeRepository,
          countryCurrencyResolver);

  @Test
  void priceFare_routePriceWinsOverMarketAndLegacy() {
    FlightEntity flight = flight("SGN", "VN", "DAD", "VN", LocalDate.parse("2026-05-01"));
    FareClassEntity business = fareClass("BUSINESS");
    when(pricingMarketProfileRepository.findByCountryCode("VN"))
        .thenReturn(Optional.of(profile("VN", "VND", false, "VN_STANDARD")));
    when(fareClassMultiplierRepository.findByFareCodeAndTripScope("BUSINESS", "DOMESTIC"))
        .thenReturn(Optional.of(multiplier("BUSINESS", "DOMESTIC", "2.10")));
    when(routeBasePriceRepository.findActiveByRouteAndCurrency("SGN", "DAD", "VND", flight.getFlightDate()))
        .thenReturn(Optional.of(routePrice("VND", "780000.00")));
    when(taxFeeRepository.findByIsActiveTrueAndApplyToAndTaxProfileCode("ADULT", "VN_STANDARD"))
        .thenReturn(List.of(tax("VAT", "PERCENT", "0.10"), tax("APT", "FIXED", "60000")));

    var pricing = service.priceFare(flight, business);

    assertTrue(pricing.isPresent());
    assertEquals(RouteBasedFarePricingService.PricingSourceLevel.ROUTE, pricing.get().sourceLevel());
    assertEquals(new BigDecimal("1638000.00"), pricing.get().baseFare());
    assertEquals(new BigDecimal("223800.00"), pricing.get().totalTaxAmount());
    assertEquals(new BigDecimal("1861800.00"), pricing.get().totalPrice());
  }

  @Test
  void priceFare_marketPriceUsedWhenRouteMissing() {
    FlightEntity flight = flight("LHR", "GB", "CDG", "DE", LocalDate.parse("2026-05-01"));
    FareClassEntity premium = fareClass("PREMIUM");
    when(pricingMarketProfileRepository.findByCountryCode("GB"))
        .thenReturn(Optional.of(profile("GB", "GBP", true, "GLOBAL_STANDARD")));
    when(fareClassMultiplierRepository.findByFareCodeAndTripScope("PREMIUM", "INTERNATIONAL"))
        .thenReturn(Optional.of(multiplier("PREMIUM", "INTERNATIONAL", "1.58")));
    when(routeBasePriceRepository.findActiveByRouteAndCurrency("LHR", "CDG", "GBP", flight.getFlightDate()))
        .thenReturn(Optional.empty());
    when(marketBasePriceRepository.findByOriginCountryCodeAndDestinationCountryCodeAndCurrency("GB", "DE", "GBP"))
        .thenReturn(Optional.of(marketPrice("GBP", "95.00")));
    when(taxFeeRepository.findByIsActiveTrueAndApplyToAndTaxProfileCode("ADULT", "GLOBAL_STANDARD"))
        .thenReturn(List.of(tax("VAT", "PERCENT", "0.10")));

    var pricing = service.priceFare(flight, premium);

    assertTrue(pricing.isPresent());
    assertEquals(RouteBasedFarePricingService.PricingSourceLevel.MARKET, pricing.get().sourceLevel());
    assertEquals(new BigDecimal("150.10"), pricing.get().baseFare());
    assertEquals(new BigDecimal("15.01"), pricing.get().totalTaxAmount());
    assertEquals(new BigDecimal("165.11"), pricing.get().totalPrice());
  }

  @Test
  void priceFare_globalFallbackUsedWhenCountryPairMissing() {
    FlightEntity flight = flight("JFK", "US", "CDG", "DE", LocalDate.parse("2026-05-01"));
    FareClassEntity economy = fareClass("ECONOMY");
    when(pricingMarketProfileRepository.findByCountryCode("US"))
        .thenReturn(Optional.of(profile("US", "USD", true, "GLOBAL_STANDARD")));
    when(fareClassMultiplierRepository.findByFareCodeAndTripScope("ECONOMY", "INTERNATIONAL"))
        .thenReturn(Optional.of(multiplier("ECONOMY", "INTERNATIONAL", "1.00")));
    when(routeBasePriceRepository.findActiveByRouteAndCurrency("JFK", "CDG", "USD", flight.getFlightDate()))
        .thenReturn(Optional.empty());
    when(marketBasePriceRepository.findByOriginCountryCodeAndDestinationCountryCodeAndCurrency("US", "DE", "USD"))
        .thenReturn(Optional.empty());
    when(marketBasePriceRepository.findByOriginCountryCodeAndDestinationCountryCodeAndCurrency("GL", "GL", "USD"))
        .thenReturn(Optional.of(marketPrice("USD", "90.00")));
    when(taxFeeRepository.findByIsActiveTrueAndApplyToAndTaxProfileCode("ADULT", "GLOBAL_STANDARD"))
        .thenReturn(List.of(tax("VAT", "PERCENT", "0.10")));

    var pricing = service.priceFare(flight, economy);

    assertTrue(pricing.isPresent());
    assertEquals(RouteBasedFarePricingService.PricingSourceLevel.GLOBAL_MARKET, pricing.get().sourceLevel());
    assertEquals(new BigDecimal("90.00"), pricing.get().baseFare());
    assertEquals(new BigDecimal("99.00"), pricing.get().totalPrice());
  }

  @Test
  void priceFare_legacyFallbackWorksAndNonVndKeepsNoFixedFees() {
    FlightEntity flight = flight("LAX", "US", "SFO", "US", LocalDate.parse("2026-05-01"));
    FareClassEntity economy = fareClass("ECONOMY");
    when(pricingMarketProfileRepository.findByCountryCode("US"))
        .thenReturn(Optional.of(profile("US", "USD", true, "GLOBAL_STANDARD")));
    when(fareClassMultiplierRepository.findByFareCodeAndTripScope("ECONOMY", "DOMESTIC"))
        .thenReturn(Optional.of(multiplier("ECONOMY", "DOMESTIC", "1.00")));
    when(routeBasePriceRepository.findActiveByRouteAndCurrency("LAX", "SFO", "USD", flight.getFlightDate()))
        .thenReturn(Optional.empty());
    when(marketBasePriceRepository.findByOriginCountryCodeAndDestinationCountryCodeAndCurrency("US", "US", "USD"))
        .thenReturn(Optional.empty());
    when(marketBasePriceRepository.findByOriginCountryCodeAndDestinationCountryCodeAndCurrency("GL", "GL", "USD"))
        .thenReturn(Optional.empty());
    when(fareClassPriceRepository.findByFareClassAndCurrencyAndCountryCode(economy, "USD", "US"))
        .thenReturn(Optional.of(legacyFarePrice("USD", "US", "60.00")));
    when(taxFeeRepository.findByIsActiveTrueAndApplyToAndTaxProfileCode("ADULT", "GLOBAL_STANDARD"))
        .thenReturn(List.of(tax("VAT", "PERCENT", "0.10")));

    var pricing = service.priceFare(flight, economy);

    assertTrue(pricing.isPresent());
    assertEquals(RouteBasedFarePricingService.PricingSourceLevel.LEGACY, pricing.get().sourceLevel());
    assertEquals(new BigDecimal("60.00"), pricing.get().baseFare());
    assertEquals(new BigDecimal("6.00"), pricing.get().totalTaxAmount());
    assertEquals(new BigDecimal("66.00"), pricing.get().totalPrice());
  }

  @Test
  void priceFare_firstIsBlockedWhenMarketProfileDisallowsIt() {
    FlightEntity flight = flight("SGN", "VN", "HAN", "VN", LocalDate.parse("2026-05-01"));
    FareClassEntity first = fareClass("FIRST");
    when(pricingMarketProfileRepository.findByCountryCode("VN"))
        .thenReturn(Optional.of(profile("VN", "VND", false, "VN_STANDARD")));

    var pricing = service.priceFare(flight, first);

    assertFalse(pricing.isPresent());
  }

  private static FlightEntity flight(
      String originCode,
      String originCountryCode,
      String destinationCode,
      String destinationCountryCode,
      LocalDate flightDate) {
    AirportEntity origin = new AirportEntity();
    origin.setCode(originCode);
    origin.setCountryCode(originCountryCode);
    origin.setCountry(originCountryCode);
    AirportEntity destination = new AirportEntity();
    destination.setCode(destinationCode);
    destination.setCountryCode(destinationCountryCode);
    destination.setCountry(destinationCountryCode);

    FlightEntity flight = new FlightEntity();
    flight.setDepartureAirport(origin);
    flight.setArrivalAirport(destination);
    flight.setFlightDate(flightDate);
    return flight;
  }

  private static FareClassEntity fareClass(String code) {
    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setCode(code);
    return fareClass;
  }

  private static PricingMarketProfileEntity profile(
      String countryCode, String currency, boolean allowFirst, String taxProfileCode) {
    PricingMarketProfileEntity profile = new PricingMarketProfileEntity();
    profile.setCountryCode(countryCode);
    profile.setCurrency(currency);
    profile.setAllowFirst(allowFirst);
    profile.setTaxProfileCode(taxProfileCode);
    return profile;
  }

  private static FareClassMultiplierEntity multiplier(
      String fareCode, String tripScope, String value) {
    FareClassMultiplierEntity entity = new FareClassMultiplierEntity();
    entity.setFareCode(fareCode);
    entity.setTripScope(tripScope);
    entity.setMultiplier(new BigDecimal(value));
    return entity;
  }

  private static RouteBasePriceEntity routePrice(String currency, String amount) {
    RouteBasePriceEntity entity = new RouteBasePriceEntity();
    entity.setCurrency(currency);
    entity.setBaseEconomyPrice(new BigDecimal(amount));
    return entity;
  }

  private static MarketBasePriceEntity marketPrice(String currency, String amount) {
    MarketBasePriceEntity entity = new MarketBasePriceEntity();
    entity.setCurrency(currency);
    entity.setBaseEconomyPrice(new BigDecimal(amount));
    return entity;
  }

  private static FareClassPriceEntity legacyFarePrice(
      String currency, String countryCode, String amount) {
    FareClassPriceEntity entity = new FareClassPriceEntity();
    entity.setCurrency(currency);
    entity.setCountryCode(countryCode);
    entity.setBasePrice(new BigDecimal(amount));
    return entity;
  }

  private static TaxFeeEntity tax(String code, String amountType, String value) {
    TaxFeeEntity tax = new TaxFeeEntity();
    tax.setCode(code);
    tax.setName(code);
    tax.setAmountType(amountType);
    tax.setValue(new BigDecimal(value));
    return tax;
  }
}
