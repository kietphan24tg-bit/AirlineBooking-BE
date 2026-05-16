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
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.entity.FlightFareEntity;
import com.example.moduleflight.repository.FareClassRepository;
import com.example.moduleflight.repository.FlightFareRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class FlightFareGenerationServiceTest {

  private final FareClassRepository fareClassRepository = mock(FareClassRepository.class);
  private final FlightFareRepository flightFareRepository = mock(FlightFareRepository.class);
  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final FlightInventoryMetadataService metadataService =
      new FlightInventoryMetadataService(jdbcTemplate);
  private final RouteBasedFarePricingService routeBasedFarePricingService =
      mock(RouteBasedFarePricingService.class);
  private final FlightFareGenerationService service =
      new FlightFareGenerationService(
          fareClassRepository,
          flightFareRepository,
          new ObjectMapper(),
          jdbcTemplate,
          metadataService,
          routeBasedFarePricingService);

  @Test
  void generateFaresForFlight_persistsRouteBasedPricingAndBreakdown() {
    FareClassEntity economy = fareClass("ECONOMY", "ECONOMY");
    when(fareClassRepository.findByCodeIn(any())).thenReturn(List.of(economy));
    when(jdbcTemplate.queryForList(any(String.class), eq(String.class), anyLong()))
        .thenReturn(List.of("ECONOMY"));
    when(flightFareRepository.findByFlightAndFareClass(any(), eq(economy)))
        .thenReturn(Optional.empty());
    when(routeBasedFarePricingService.priceFare(any(), eq(economy)))
        .thenReturn(
            Optional.of(
                new RouteBasedFarePricingService.PricingResult(
                    "USD",
                    PricingTripScope.INTERNATIONAL,
                    RouteBasedFarePricingService.PricingSourceLevel.ROUTE,
                    "GLOBAL_STANDARD",
                    new BigDecimal("120.00"),
                    new BigDecimal("12.00"),
                    new BigDecimal("132.00"),
                    List.of(
                        new RouteBasedFarePricingService.TaxLineItem(
                            "GLOBAL_VAT_10", "Standard VAT", new BigDecimal("12.00"), "USD")))));
    List<FlightFareEntity> savedFares = captureSavedFares();

    service.generateFaresForFlight(flight("US", "United States"));

    FlightFareEntity saved = savedFares.getFirst();
    assertEquals(new BigDecimal("120.00"), saved.getBasePrice());
    assertEquals(new BigDecimal("12.00"), saved.getTotalTaxAmount());
    assertEquals(new BigDecimal("132.00"), saved.getTotalPrice());
    assertEquals(
        "[{\"code\":\"GLOBAL_VAT_10\",\"name\":\"Standard VAT\",\"amount\":12.00}]",
        saved.getTaxBreakdown());
  }

  @Test
  void generateFaresForFlight_skipsFareWhenPricingServiceDisallowsIt() {
    FareClassEntity first = fareClass("FIRST", "FIRST");
    when(fareClassRepository.findByCodeIn(any())).thenReturn(List.of(first));
    when(jdbcTemplate.queryForList(any(String.class), eq(String.class), anyLong()))
        .thenReturn(List.of("FIRST"));
    when(flightFareRepository.findByFlightAndFareClass(any(), eq(first)))
        .thenReturn(Optional.empty());
    when(routeBasedFarePricingService.priceFare(any(), eq(first))).thenReturn(Optional.empty());
    List<FlightFareEntity> savedFares = captureSavedFares();

    service.generateFaresForFlight(flight("VN", "Vietnam"));

    assertEquals(0, savedFares.size());
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
    airport.setCode("AAA");
    airport.setCountryCode(countryCode);
    airport.setCountry(country);

    FlightEntity flight = new FlightEntity();
    flight.setId(20L);
    flight.setAircraft(aircraft);
    flight.setDepartureAirport(airport);
    flight.setArrivalAirport(airport);
    return flight;
  }

  private static FareClassEntity fareClass(String code, String cabinClass) {
    FareClassEntity fareClass = new FareClassEntity();
    fareClass.setCode(code);
    fareClass.setCabinClass(cabinClass);
    return fareClass;
  }
}
