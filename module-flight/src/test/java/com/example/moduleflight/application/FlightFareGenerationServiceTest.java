package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.moduleflight.entity.AircraftEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FareClassEntity;
import com.example.moduleflight.entity.FareClassPriceEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.entity.TaxFeeEntity;
import com.example.moduleflight.repository.FareClassPriceRepository;
import com.example.moduleflight.repository.FareClassRepository;
import com.example.moduleflight.repository.FlightFareRepository;
import com.example.moduleflight.repository.TaxFeeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class FlightFareGenerationServiceTest {

  private final FareClassRepository fareClassRepository = mock(FareClassRepository.class);
  private final FareClassPriceRepository fareClassPriceRepository =
      mock(FareClassPriceRepository.class);
  private final TaxFeeRepository taxFeeRepository = mock(TaxFeeRepository.class);
  private final FlightFareRepository flightFareRepository = mock(FlightFareRepository.class);
  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final CountryCurrencyResolver countryCurrencyResolver = new CountryCurrencyResolver();
  private final FlightInventoryMetadataService metadataService =
      new FlightInventoryMetadataService(jdbcTemplate);
  private final FlightFareGenerationService service =
      new FlightFareGenerationService(
          fareClassRepository,
          fareClassPriceRepository,
          taxFeeRepository,
          flightFareRepository,
          new ObjectMapper(),
          jdbcTemplate,
          metadataService,
          countryCurrencyResolver);

  @Test
  void generateFaresForFlight_nonVndCurrency_doesNotAddVndFixedFees() {
    FareClassEntity economy = fareClass("ECONOMY", "ECONOMY");
    when(fareClassRepository.findByCodeIn(any())).thenReturn(List.of(economy));
    when(fareClassPriceRepository.findByFareClassAndCurrencyAndCountryCode(economy, "USD", "US"))
        .thenReturn(Optional.of(price(economy, "USD", "US", "60")));
    when(taxFeeRepository.findByIsActiveTrueAndApplyTo("ADULT"))
        .thenReturn(List.of(tax("VAT_DOM", "PERCENT", "0.10"), tax("APT_FEE", "FIXED", "60000")));
    when(jdbcTemplate.queryForList(any(String.class), eq(String.class), anyLong()))
        .thenReturn(List.of("ECONOMY"));
    when(flightFareRepository.findByFlightAndFareClass(any(), eq(economy)))
        .thenReturn(Optional.empty());
    List<FlightFareEntity> savedFares = captureSavedFares();

    service.generateFaresForFlight(flight("US", "United States"));

    FlightFareEntity saved = savedFares.getFirst();
    assertEquals(new BigDecimal("60"), saved.getBasePrice());
    assertEquals(new BigDecimal("6.00"), saved.getTotalTaxAmount());
    assertEquals(new BigDecimal("66.00"), saved.getTotalPrice());
  }

  @Test
  void generateFaresForFlight_vndCurrency_keepsFixedFees() {
    FareClassEntity economy = fareClass("ECONOMY", "ECONOMY");
    when(fareClassRepository.findByCodeIn(any())).thenReturn(List.of(economy));
    when(fareClassPriceRepository.findByFareClassAndCurrencyAndCountryCode(economy, "VND", "VN"))
        .thenReturn(Optional.of(price(economy, "VND", "VN", "1200000")));
    when(taxFeeRepository.findByIsActiveTrueAndApplyTo("ADULT"))
        .thenReturn(List.of(tax("VAT_DOM", "PERCENT", "0.10"), tax("APT_FEE", "FIXED", "60000")));
    when(jdbcTemplate.queryForList(any(String.class), eq(String.class), anyLong()))
        .thenReturn(List.of("ECONOMY"));
    when(flightFareRepository.findByFlightAndFareClass(any(), eq(economy)))
        .thenReturn(Optional.empty());
    List<FlightFareEntity> savedFares = captureSavedFares();

    service.generateFaresForFlight(flight("VN", "Vietnam"));

    FlightFareEntity saved = savedFares.getFirst();
    assertEquals(new BigDecimal("1200000"), saved.getBasePrice());
    assertEquals(new BigDecimal("180000.00"), saved.getTotalTaxAmount());
    assertEquals(new BigDecimal("1380000.00"), saved.getTotalPrice());
  }

  private List<FlightFareEntity> captureSavedFares() {
    List<FlightFareEntity> savedFares = new ArrayList<>();
    org.mockito.Mockito.when(flightFareRepository.saveAll(any()))
        .thenAnswer(
            invocation -> {
              Iterable<?> fares = invocation.getArgument(0);
              for (Object fare : fares) {
                savedFares.add((FlightFareEntity) fare);
              }
              return savedFares;
            });
    return savedFares;
  }

  private static FlightEntity flight(String countryCode, String country) {
    AircraftEntity aircraft = new AircraftEntity();
    aircraft.setId(10L);

    AirportEntity airport = new AirportEntity();
    airport.setCountryCode(countryCode);
    airport.setCountry(country);

    FlightEntity flight = new FlightEntity();
    flight.setId(20L);
    flight.setAircraft(aircraft);
    flight.setDepartureAirport(airport);
    return flight;
  }

  private static FareClassEntity fareClass(String code, String cabinClass) {
    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setCode(code);
    fareClass.setCabinClass(cabinClass);
    return fareClass;
  }

  private static FareClassPriceEntity price(
      FareClassEntity fareClass, String currency, String countryCode, String basePrice) {
    FareClassPriceEntity price = new FareClassPriceEntity();
    price.setFareClass(fareClass);
    price.setCurrency(currency);
    price.setCountryCode(countryCode);
    price.setBasePrice(new BigDecimal(basePrice));
    return price;
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
