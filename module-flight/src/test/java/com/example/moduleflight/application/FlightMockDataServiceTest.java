package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleflight.config.FlightCoverageProperties;
import com.example.moduleflight.config.FlightMockDataProperties;
import com.example.moduleflight.domain.FlightSource;
import com.example.moduleflight.entity.AircraftEntity;
import com.example.moduleflight.entity.AircraftLayoutEntity;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.FlightEntity;
import com.example.moduleflight.repository.AircraftLayoutRepository;
import com.example.moduleflight.repository.AirlineRepository;
import com.example.moduleflight.repository.AirportRepository;
import com.example.moduleflight.repository.FlightFareRepository;
import com.example.moduleflight.repository.FlightRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class FlightMockDataServiceTest {

  private final FlightRepository flightRepository = Mockito.mock(FlightRepository.class);
  private final AirlineRepository airlineRepository = Mockito.mock(AirlineRepository.class);
  private final AirportRepository airportRepository = Mockito.mock(AirportRepository.class);
  private final AircraftLayoutRepository aircraftLayoutRepository =
      Mockito.mock(AircraftLayoutRepository.class);
  private final FlightFareRepository flightFareRepository =
      Mockito.mock(FlightFareRepository.class);
  private final FlightSeatGenerationService flightSeatGenerationService =
      Mockito.mock(FlightSeatGenerationService.class);
  private final FlightFareGenerationService flightFareGenerationService =
      Mockito.mock(FlightFareGenerationService.class);
  private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
  private final FlightInventoryMetadataService flightInventoryMetadataService =
      Mockito.mock(FlightInventoryMetadataService.class);

  @Test
  void reseedMockFlights_usesCoverageCountryFiltersAndCreatesSeededFlights() {
    FlightMockDataService service = newService(false);
    mockAuditQueries();
    when(aircraftLayoutRepository.findAll()).thenReturn(List.of(layout(aircraft(1L, "A321"))));
    when(airlineRepository.findAll())
        .thenReturn(List.of(airline(10L, "VN", "VN"), airline(20L, "SQ", "SG")));
    when(airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode("VN"))
        .thenReturn(List.of(airport(1L, "HAN", "VN"), airport(2L, "SGN", "VN")));
    when(airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode("SG"))
        .thenReturn(List.of(airport(3L, "SIN", "SG")));
    when(flightRepository.count()).thenReturn(0L, 2L);
    when(flightRepository.saveAll(any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              List<FlightEntity> flights = invocation.getArgument(0);
              long id = 100L;
              for (FlightEntity flight : flights) {
                flight.setId(id++);
              }
              return flights;
            });

    FlightMockDataService.MockSummary summary = service.reseedMockFlights();

    assertEquals(2, summary.generatedFlights());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<FlightEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(flightRepository).saveAll(captor.capture());
    List<FlightEntity> savedFlights = captor.getValue();
    assertEquals(2, savedFlights.size());
    assertTrue(
        savedFlights.stream()
            .allMatch(
                flight ->
                    "VN".equals(flight.getDepartureAirport().getCountryCode())
                        && "VN".equals(flight.getArrivalAirport().getCountryCode())));
    assertTrue(
        savedFlights.stream()
            .allMatch(flight -> FlightSource.SEEDED.equals(flight.getFlightSource())));
    verify(flightSeatGenerationService, Mockito.times(2)).generateForFlight(any(FlightEntity.class));
    verify(flightFareGenerationService, Mockito.times(2))
        .generateFaresForFlight(any(FlightEntity.class));
  }

  @Test
  void reseedMockFlights_abortsWhenTicketsExistAndForceDisabled() {
    FlightMockDataService service = newService(false);
    mockAuditQueries();
    when(jdbcTemplate.queryForObject("select count(*) from tickets", Long.class)).thenReturn(1L);

    FlightMockDataService.MockSummary summary = service.reseedMockFlights();

    assertEquals("tickets_exist", summary.status());
    verify(flightRepository, never()).saveAll(any());
  }

  private FlightMockDataService newService(boolean forceWhenTicketsExist) {
    return new FlightMockDataService(
        new FlightCoverageProperties(true, 30, 1, "VN,SG", 3, 20000, "", "SG", "SG"),
        new FlightMockDataProperties(
            true, false, "0 0 4 * * *", forceWhenTicketsExist, 2, 1, 2, 1, "", "", ""),
        flightRepository,
        airlineRepository,
        airportRepository,
        aircraftLayoutRepository,
        flightFareRepository,
        flightSeatGenerationService,
        flightFareGenerationService,
        jdbcTemplate,
        flightInventoryMetadataService);
  }

  private void mockAuditQueries() {
    when(jdbcTemplate.queryForObject(anyString(), Mockito.eq(Long.class))).thenReturn(0L);
    when(jdbcTemplate.queryForList(anyString())).thenReturn(List.<Map<String, Object>>of());
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

  private static AircraftLayoutEntity layout(AircraftEntity aircraft) {
    AircraftLayoutEntity layout = new AircraftLayoutEntity();
    layout.setId(11L);
    layout.setAircraft(aircraft);
    return layout;
  }

  private static AirlineEntity airline(Long id, String code, String countryCode) {
    AirlineEntity airline = new AirlineEntity();
    airline.setId(id);
    airline.setCode(code);
    airline.setName(code);
    airline.setCountryCode(countryCode);
    airline.setCreatedAt(OffsetDateTime.now());
    airline.setUpdatedAt(OffsetDateTime.now());
    return airline;
  }

  private static AirportEntity airport(Long id, String code, String countryCode) {
    AirportEntity airport = new AirportEntity();
    airport.setId(id);
    airport.setCode(code);
    airport.setName(code);
    airport.setCity(code);
    airport.setCountry(countryCode);
    airport.setCountryCode(countryCode);
    airport.setTimeZone("Asia/Ho_Chi_Minh");
    airport.setCreatedAt(OffsetDateTime.now());
    airport.setUpdatedAt(OffsetDateTime.now());
    return airport;
  }
}
