package com.example.appbootstrap.controller.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.appbootstrap.controller.flight.dto.AirportByCountryItemResponse;
import com.example.moduleflight.application.AirportGroupedItem;
import com.example.moduleflight.application.AirportQueryService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AirportsControllerTest {

  @Mock private AirportQueryService airportQueryService;

  private AirportsController controller;

  @BeforeEach
  void setUp() {
    controller = new AirportsController(airportQueryService);
  }

  @Test
  void groupedByCountry_mapsToResponseDtos() {
    Map<String, List<AirportGroupedItem>> raw = new LinkedHashMap<>();
    raw.put("Thailand", List.of(new AirportGroupedItem(1L, "BKK", "Suvarnabhumi", "Bangkok")));
    when(airportQueryService.findGroupedByCountry(null)).thenReturn(raw);

    Map<String, List<AirportByCountryItemResponse>> result = controller.groupedByCountry(null);

    assertEquals(1, result.size());
    assertEquals(1, result.get("Thailand").size());
    assertEquals(1L, result.get("Thailand").getFirst().id());
    assertEquals("BKK", result.get("Thailand").getFirst().code());
    assertEquals("Suvarnabhumi", result.get("Thailand").getFirst().name());
    assertEquals("Bangkok", result.get("Thailand").getFirst().city());
  }

  @Test
  void groupedByCountry_passesCountryFilterToService() {
    when(airportQueryService.findGroupedByCountry("Vietnam")).thenReturn(new LinkedHashMap<>());

    controller.groupedByCountry("Vietnam");

    verify(airportQueryService).findGroupedByCountry("Vietnam");
  }
}
