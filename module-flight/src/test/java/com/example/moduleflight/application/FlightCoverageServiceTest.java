package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleflight.config.FlightCoverageProperties;
import com.example.moduleflight.entity.AircraftEntity;
import com.example.moduleflight.entity.AircraftLayoutEntity;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.repository.AircraftLayoutRepository;
import com.example.moduleflight.repository.AirlineRepository;
import com.example.moduleflight.repository.AirportRepository;
import com.example.moduleflight.repository.FlightRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FlightCoverageServiceTest {

  private final FlightRepository flightRepository = Mockito.mock(FlightRepository.class);
  private final AirlineRepository airlineRepository = Mockito.mock(AirlineRepository.class);
  private final AirportRepository airportRepository = Mockito.mock(AirportRepository.class);
  private final AircraftLayoutRepository aircraftLayoutRepository =
      Mockito.mock(AircraftLayoutRepository.class);
  private final FlightSeatGenerationService flightSeatGenerationService =
      Mockito.mock(FlightSeatGenerationService.class);
  private final FlightFareGenerationService flightFareGenerationService =
      Mockito.mock(FlightFareGenerationService.class);

  @Test
  void fillCoverageGaps_createsTargetFlightsPerRoutePerDay() {
    FlightCoverageService service = newService(3);
    mockCommonPools();
    when(flightRepository.countByDepartureAirport_IdAndArrivalAirport_IdAndFlightDate(
            any(), any(), any()))
        .thenReturn(0L);
    when(flightRepository.findByAirlineAndFlightNumberAndFlightDate(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(flightRepository.save(any(FlightEntity.class)))
        .thenAnswer(
            invocation -> {
              FlightEntity flight = invocation.getArgument(0);
              flight.setId(100L);
              return flight;
            });

    int created = service.fillCoverageGaps();

    assertEquals(6, created);
    verify(flightRepository, times(6)).save(any(FlightEntity.class));
    verify(flightSeatGenerationService, times(6)).generateForFlight(any(FlightEntity.class));
    verify(flightFareGenerationService, times(6)).generateFaresForFlight(any(FlightEntity.class));
  }

  @Test
  void fillCoverageGaps_onlyCreatesMissingFlightsWhenRouteAlreadyHasCoverage() {
    FlightCoverageService service = newService(3);
    mockCommonPools();
    when(flightRepository.countByDepartureAirport_IdAndArrivalAirport_IdAndFlightDate(
            any(), any(), any()))
        .thenReturn(2L);
    when(flightRepository.findByAirlineAndFlightNumberAndFlightDate(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(flightRepository.save(any(FlightEntity.class)))
        .thenAnswer(
            invocation -> {
              FlightEntity flight = invocation.getArgument(0);
              flight.setId(100L);
              return flight;
            });

    int created = service.fillCoverageGaps();

    assertEquals(2, created);
    verify(flightRepository, times(2)).save(any(FlightEntity.class));
  }

  private FlightCoverageService newService(int flightsPerRoutePerDay) {
    return new FlightCoverageService(
        new FlightCoverageProperties(true, 1, 1, "VN", flightsPerRoutePerDay, 50, "", "", ""),
        flightRepository,
        airlineRepository,
        airportRepository,
        aircraftLayoutRepository,
        flightSeatGenerationService,
        flightFareGenerationService);
  }

  private void mockCommonPools() {
    AircraftEntity aircraft = aircraft(1L, "A321");
    AircraftLayoutEntity layout = new AircraftLayoutEntity();
    layout.setId(11L);
    layout.setAircraft(aircraft);

    when(aircraftLayoutRepository.findAll()).thenReturn(List.of(layout));
    when(airlineRepository.findAll()).thenReturn(List.of(airline(10L, "VN"), airline(20L, "VJ")));
    when(airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode("VN"))
        .thenReturn(List.of(airport(1L, "HAN"), airport(2L, "SGN")));
  }

  private static AircraftEntity aircraft(Long id, String model) {
    AircraftEntity aircraft = new AircraftEntity();
    aircraft.setId(id);
    aircraft.setModel(model);
    aircraft.setManufacturer("Airbus");
    aircraft.setTotalSeats(180);
    aircraft.setCreatedAt(OffsetDateTime.now());
    aircraft.setUpdatedAt(OffsetDateTime.now());
    return aircraft;
  }

  private static AirlineEntity airline(Long id, String code) {
    AirlineEntity airline = new AirlineEntity();
    airline.setId(id);
    airline.setCode(code);
    airline.setName(code);
    airline.setCountryCode("VN");
    airline.setCreatedAt(OffsetDateTime.now());
    airline.setUpdatedAt(OffsetDateTime.now());
    return airline;
  }

  private static AirportEntity airport(Long id, String code) {
    AirportEntity airport = new AirportEntity();
    airport.setId(id);
    airport.setCode(code);
    airport.setName(code);
    airport.setCity(code);
    airport.setCountry("Vietnam");
    airport.setCountryCode("VN");
    airport.setTimeZone("Asia/Ho_Chi_Minh");
    airport.setCreatedAt(OffsetDateTime.now());
    airport.setUpdatedAt(OffsetDateTime.now());
    return airport;
  }
}
