package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.moduleflight.config.AirportMarketProperties;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.UserRecentAirportEntity;
import com.example.moduleflight.repository.AirportRepository;
import com.example.moduleflight.repository.UserRecentAirportRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AirportPickerServiceTest {

  @Mock private AirportRepository airportRepository;
  @Mock private UserRecentAirportRepository userRecentAirportRepository;

  private AirportPickerService service;

  @BeforeEach
  void setUp() {
    service =
        new AirportPickerService(
            airportRepository,
            userRecentAirportRepository,
            new AirportMarketService(
                userRecentAirportRepository, new AirportMarketProperties("VN", null), List.of()));
  }

  @Test
  void picker_prefersRecentDepartureMarketForLoggedInUser() {
    AirportEntity nrt = airport(10L, "NRT", "Narita", "Tokyo", "Japan", "JP");
    when(userRecentAirportRepository.findByUserIdOrderByLastUsedAtDesc(any(), any(Pageable.class)))
        .thenReturn(List.of(recent(nrt, AirportUsageType.DEPARTURE)));
    when(userRecentAirportRepository.findByUserIdAndUsageTypeInOrderByLastUsedAtDesc(
            any(), any(), any(Pageable.class)))
        .thenReturn(List.of(recent(nrt, AirportUsageType.DEPARTURE)));
    when(airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode("JP"))
        .thenReturn(List.of(airport(11L, "HND", "Haneda", "Tokyo", "Japan", "JP")));

    AirportPickerResult result =
        service.picker(7L, "vi-VN,vi;q=0.9", "Asia/Ho_Chi_Minh", null, null, null, null, null);

    assertEquals("JP", result.market());
    assertEquals("NRT", result.recent().getFirst().code());
    assertEquals("HND", result.suggested().getFirst().code());
  }

  @Test
  void picker_detectsMarketFromAcceptLanguageBeforeTimezone() {
    when(airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode("JP"))
        .thenReturn(List.of(airport(11L, "HND", "Haneda", "Tokyo", "Japan", "JP")));

    AirportPickerResult result =
        service.picker(null, "ja-JP,ja;q=0.9", "Asia/Ho_Chi_Minh", null, null, null, null, null);

    assertEquals("JP", result.market());
    assertTrue(result.recent().isEmpty());
  }

  @Test
  void picker_usesRegionSubtagFromAcceptLanguage() {
    when(airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode("CA"))
        .thenReturn(List.of(airport(20L, "YYZ", "Pearson", "Toronto", "Canada", "CA")));

    AirportPickerResult result =
        service.picker(null, "en-CA,en;q=0.9", "Asia/Ho_Chi_Minh", null, null, null, null, null);

    assertEquals("CA", result.market());
    assertEquals("YYZ", result.suggested().getFirst().code());
  }

  @Test
  void picker_usesTimezoneBeforeLanguageOnlyAllowlist() {
    when(airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode("JP"))
        .thenReturn(List.of(airport(11L, "HND", "Haneda", "Tokyo", "Japan", "JP")));

    AirportPickerResult result =
        service.picker(null, "vi", "Asia/Tokyo", null, null, null, null, null);

    assertEquals("JP", result.market());
    assertEquals("HND", result.suggested().getFirst().code());
  }

  @Test
  void picker_detectsMarketFromTimezoneWhenLanguageIsAbsent() {
    when(airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode("VN"))
        .thenReturn(
            List.of(airport(1L, "SGN", "Tan Son Nhat", "Ho Chi Minh City", "Vietnam", "VN")));

    AirportPickerResult result =
        service.picker(null, null, "Asia/Ho_Chi_Minh", null, null, null, null, null);

    assertEquals("VN", result.market());
    assertEquals("SGN", result.suggested().getFirst().code());
  }

  @Test
  void picker_fallsBackToVietnamAndPrioritizesVietnamAirportCodes() {
    when(airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode("VN"))
        .thenReturn(
            List.of(
                airport(9L, "VCA", "Can Tho", "Can Tho", "Vietnam", "VN"),
                airport(2L, "HAN", "Noi Bai", "Hanoi", "Vietnam", "VN"),
                airport(1L, "SGN", "Tan Son Nhat", "Ho Chi Minh City", "Vietnam", "VN")));

    AirportPickerResult result = service.picker(null, null, null, null, null, null, null, null);

    assertEquals("VN", result.market());
    assertEquals("SGN", result.suggested().get(0).code());
    assertEquals("HAN", result.suggested().get(1).code());
  }

  @Test
  void picker_fallsBackToCountryNameWhenCountryCodeHasNoRows() {
    when(airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode("FR"))
        .thenReturn(List.of());
    when(airportRepository.findByCountryIgnoreCaseOrderByCityNameCode("France"))
        .thenReturn(List.of(airport(30L, "CDG", "Charles de Gaulle", "Paris", "France", null)));

    AirportPickerResult result =
        service.picker(null, "fr-FR,fr;q=0.9", null, null, null, null, null, null);

    assertEquals("FR", result.market());
    assertEquals("CDG", result.suggested().getFirst().code());
  }

  @Test
  void picker_excludesRecentAirportsFromSuggestedAndClampsInitialItems() {
    AirportEntity sgn = airport(1L, "SGN", "Tan Son Nhat", "Ho Chi Minh City", "Vietnam", "VN");
    when(userRecentAirportRepository.findByUserIdOrderByLastUsedAtDesc(any(), any(Pageable.class)))
        .thenReturn(List.of(recent(sgn, AirportUsageType.DEPARTURE)));
    when(userRecentAirportRepository.findByUserIdAndUsageTypeInOrderByLastUsedAtDesc(
            any(), any(), any(Pageable.class)))
        .thenReturn(List.of(recent(sgn, AirportUsageType.DEPARTURE)));
    when(airportRepository.findByCountryCodeIgnoreCaseOrderByCityNameCode("VN"))
        .thenReturn(
            List.of(
                sgn,
                airport(2L, "HAN", "Noi Bai", "Hanoi", "Vietnam", "VN"),
                airport(3L, "DAD", "Da Nang", "Da Nang", "Vietnam", "VN"),
                airport(4L, "PQC", "Phu Quoc", "Phu Quoc", "Vietnam", "VN"),
                airport(5L, "CXR", "Cam Ranh", "Nha Trang", "Vietnam", "VN"),
                airport(6L, "HUI", "Phu Bai", "Hue", "Vietnam", "VN"),
                airport(7L, "VII", "Vinh", "Vinh", "Vietnam", "VN"),
                airport(8L, "VCA", "Can Tho", "Can Tho", "Vietnam", "VN"),
                airport(9L, "VDH", "Dong Hoi", "Dong Hoi", "Vietnam", "VN"),
                airport(10L, "BMV", "Buon Ma Thuot", "Buon Ma Thuot", "Vietnam", "VN"),
                airport(11L, "THD", "Tho Xuan", "Thanh Hoa", "Vietnam", "VN"),
                airport(12L, "PXU", "Pleiku", "Pleiku", "Vietnam", "VN")));

    AirportPickerResult result = service.picker(7L, null, null, null, null, 99, 99, 99);

    assertEquals("VN", result.market());
    assertEquals("SGN", result.recent().getFirst().code());
    assertTrue(result.suggested().stream().noneMatch(item -> item.code().equals("SGN")));
    assertTrue(result.initialItems().size() >= 8);
    assertTrue(result.initialItems().size() <= 12);
  }

  @Test
  void search_ranksDeduplicatesCaseInsensitivelyAndAppliesLimit() {
    AirportEntity exact = airport(1L, "SG", "Exact Code", "Zulu City", "Vietnam", "VN");
    AirportEntity codePrefix =
        airport(2L, "SGN", "Tan Son Nhat", "Ho Chi Minh City", "Vietnam", "VN");
    AirportEntity cityPrefix = airport(3L, "AAA", "City Prefix", "Sg City", "Vietnam", "VN");
    AirportEntity nameContains =
        airport(4L, "BBB", "Best Sg Airport", "Alpha City", "Vietnam", "VN");
    AirportEntity countryContains =
        airport(5L, "CCC", "Country Contains", "Beta City", "Sgland", "SG");
    AirportEntity duplicateCodePrefix =
        airport(2L, "SGN", "Tan Son Nhat Duplicate", "Ho Chi Minh City", "Vietnam", "VN");
    when(airportRepository.searchCandidates("sg", "sg%", "%sg%"))
        .thenReturn(
            List.of(
                countryContains, nameContains, duplicateCodePrefix, cityPrefix, codePrefix, exact));

    List<AirportPickerItem> result = service.search(" sG ", 4);

    assertEquals(4, result.size());
    assertEquals("SG", result.get(0).code());
    assertEquals("SGN", result.get(1).code());
    assertEquals("AAA", result.get(2).code());
    assertEquals("BBB", result.get(3).code());
    assertEquals(1, result.stream().filter(item -> item.id() == 2L).count());
    verify(airportRepository).searchCandidates("sg", "sg%", "%sg%");
  }

  @Test
  void search_returnsEmptyListForBlankQuery() {
    List<AirportPickerItem> result = service.search("   ", 10);

    assertTrue(result.isEmpty());
    verifyNoInteractions(airportRepository);
  }

  private static UserRecentAirportEntity recent(AirportEntity airport, AirportUsageType usageType) {
    UserRecentAirportEntity recent = new UserRecentAirportEntity();
    recent.setUserId(7L);
    recent.setAirport(airport);
    recent.setUsageType(usageType);
    recent.setLastUsedAt(OffsetDateTime.parse("2026-04-18T00:00:00Z"));
    recent.setSearchCount(1);
    return recent;
  }

  private static AirportEntity airport(
      Long id, String code, String name, String city, String country, String countryCode) {
    AirportEntity airport = new AirportEntity();
    airport.setId(id);
    airport.setCode(code);
    airport.setName(name);
    airport.setCity(city);
    airport.setCountry(country);
    airport.setCountryCode(countryCode);
    airport.setTimeZone("UTC");
    airport.setCreatedAt(OffsetDateTime.parse("2026-04-18T00:00:00Z"));
    airport.setUpdatedAt(OffsetDateTime.parse("2026-04-18T00:00:00Z"));
    return airport;
  }
}
