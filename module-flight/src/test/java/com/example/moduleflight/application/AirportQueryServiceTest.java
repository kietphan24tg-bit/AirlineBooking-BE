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
  @Mock private AirportMarketService airportMarketService;

  private AirportQueryService service;

  @BeforeEach
  void setUp() {
    service = new AirportQueryService(airportRepository, airportMarketService);
  }

  @Test
  void findGroupedByCountry_groupsAndSortsByCountryThenName() {
    when(airportRepository.findAllForGroupedByCountry())
        .thenReturn(
            List.of(
                projection(1L, "BKK", "Suvarnabhumi", "Bangkok", "Thailand", "TH"),
                projection(2L, "ICN", "Incheon", "Seoul", "South Korea", "KR"),
                projection(3L, "DMK", "Don Mueang", "Bangkok", "Thailand", "TH"),
                projection(4L, "PUS", "Gimhae", "Busan", null, null)));

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
                projection(3L, "DMK", "Don Mueang", "Bangkok", "Thailand", "TH"),
                projection(1L, "BKK", "Suvarnabhumi", "Bangkok", "Thailand", "TH")));

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

  @Test
  void findGroupedByDetectedMarket_putsDetectedCountryFirstAndPrioritizesVietnamAirports() {
    AirportMarketContext context =
        new AirportMarketContext(null, "vi-VN,vi;q=0.9", "Asia/Tokyo", "JP", null);
    when(airportMarketService.detectMarket(context)).thenReturn("VN");
    when(airportMarketService.countryNameFromMarket("VN")).thenReturn("Vietnam");
    when(airportRepository.findAllForGroupedByCountry())
        .thenReturn(
            List.of(
                projection(10L, "BKK", "Suvarnabhumi", "Bangkok", "Thailand", "TH"),
                projection(3L, "DAD", "Da Nang", "Da Nang", "Vietnam", "VN"),
                projection(2L, "HAN", "Noi Bai", "Hanoi", "Vietnam", "VN"),
                projection(1L, "SGN", "Tan Son Nhat", "Ho Chi Minh City", "Vietnam", "VN"),
                projection(4L, "VCA", "Can Tho", "Can Tho", "Vietnam", "VN")));

    Map<String, List<AirportGroupedItem>> grouped = service.findGroupedByDetectedMarket(context);

    assertEquals(List.of("Vietnam", "Thailand"), grouped.keySet().stream().toList());
    assertEquals(
        List.of("SGN", "HAN", "DAD", "VCA"),
        grouped.get("Vietnam").stream().map(AirportGroupedItem::code).toList());
  }

  @Test
  void findGroupedByDetectedMarket_matchesCountryNameWhenCountryCodeIsMissing() {
    AirportMarketContext context =
        new AirportMarketContext(null, "fr-FR,fr;q=0.9", null, null, null);
    when(airportMarketService.detectMarket(context)).thenReturn("FR");
    when(airportMarketService.countryNameFromMarket("FR")).thenReturn("France");
    when(airportRepository.findAllForGroupedByCountry())
        .thenReturn(
            List.of(
                projection(1L, "BKK", "Suvarnabhumi", "Bangkok", "Thailand", "TH"),
                projection(2L, "CDG", "Charles de Gaulle", "Paris", "France", null)));

    Map<String, List<AirportGroupedItem>> grouped = service.findGroupedByDetectedMarket(context);

    assertEquals(List.of("France", "Thailand"), grouped.keySet().stream().toList());
  }

  @Test
  void findGroupedByDetectedMarket_keepsExistingOrderWhenDetectedCountryIsMissing() {
    AirportMarketContext context =
        new AirportMarketContext(null, "en-AU,en;q=0.9", null, null, null);
    when(airportMarketService.detectMarket(context)).thenReturn("AU");
    when(airportMarketService.countryNameFromMarket("AU")).thenReturn("Australia");
    when(airportRepository.findAllForGroupedByCountry())
        .thenReturn(
            List.of(
                projection(1L, "BKK", "Suvarnabhumi", "Bangkok", "Thailand", "TH"),
                projection(2L, "ICN", "Incheon", "Seoul", "South Korea", "KR")));

    Map<String, List<AirportGroupedItem>> grouped = service.findGroupedByDetectedMarket(context);

    assertEquals(List.of("Thailand", "South Korea"), grouped.keySet().stream().toList());
  }

  private static AirportGroupedProjection projection(
      Long id, String code, String name, String city, String country, String countryCode) {
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

      @Override
      public String getCountryCode() {
        return countryCode;
      }
    };
  }
}
