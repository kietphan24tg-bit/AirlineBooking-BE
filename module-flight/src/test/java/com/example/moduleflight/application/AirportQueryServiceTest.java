package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.moduleflight.repository.AirportGroupedProjection;
import com.example.moduleflight.repository.AirportRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AirportQueryServiceTest {

  @Mock private AirportRepository airportRepository;

  private AirportQueryService service;

  @BeforeEach
  void setUp() {
    service = new AirportQueryService(airportRepository);
  }

  @Test
  void findGroupedByCountry_groupsAndSortsByCountryThenName() {
    when(airportRepository.findAllForGroupedByCountry())
        .thenReturn(
            List.of(
                projection(1L, "BKK", "Suvarnabhumi", "Bangkok", "Thailand"),
                projection(2L, "ICN", "Incheon", "Seoul", "South Korea"),
                projection(3L, "DMK", "Don Mueang", "Bangkok", "Thailand"),
                projection(4L, "PUS", "Gimhae", "Busan", null)));

    Map<String, List<AirportGroupedItem>> grouped = service.findGroupedByCountry(null);

    assertEquals(List.of("Thailand", "South Korea", "UNKNOWN"), grouped.keySet().stream().toList());
    assertEquals(1, grouped.get("South Korea").size());
    assertEquals("ICN", grouped.get("South Korea").getFirst().code());
    assertEquals(2, grouped.get("Thailand").size());
    assertEquals("BKK", grouped.get("Thailand").getFirst().code());
    assertEquals("DMK", grouped.get("Thailand").get(1).code());
    assertEquals(1, grouped.get("UNKNOWN").size());
    assertEquals("PUS", grouped.get("UNKNOWN").getFirst().code());
  }

  @Test
  void findGroupedByCountry_withFilter_returnsSingleKeyOrderedByName() {
    when(airportRepository.findByCountryForGrouped("Thailand"))
        .thenReturn(
            List.of(
                projection(3L, "DMK", "Don Mueang", "Bangkok", "Thailand"),
                projection(1L, "BKK", "Suvarnabhumi", "Bangkok", "Thailand")));

    Map<String, List<AirportGroupedItem>> grouped = service.findGroupedByCountry("Thailand");

    assertEquals(1, grouped.size());
    assertEquals(
        List.of("DMK", "BKK"),
        grouped.get("Thailand").stream().map(AirportGroupedItem::code).toList());
  }

  @Test
  void findGroupedByCountry_withFilter_emptyListStillReturnsKey() {
    when(airportRepository.findByCountryForGrouped("Nowhere")).thenReturn(List.of());

    Map<String, List<AirportGroupedItem>> grouped = service.findGroupedByCountry("Nowhere");

    assertEquals(1, grouped.size());
    assertEquals(List.of(), grouped.get("Nowhere"));
  }

  private static AirportGroupedProjection projection(
      Long id, String code, String name, String city, String country) {
    return new AirportGroupedProjection() {
      @Override
      public Long getId() {
        return id;
      }

      @Override
      public String getCode() {
        return code;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getCity() {
        return city;
      }

      @Override
      public String getCountry() {
        return country;
      }
    };
  }
}
