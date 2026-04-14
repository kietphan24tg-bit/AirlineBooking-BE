package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleflight.config.AirlineImportProperties;
import com.example.moduleflight.entity.AirlineEntity;
import com.example.moduleflight.integration.airlabs.AirlabsAirlinesClient;
import com.example.moduleflight.integration.airlabs.dto.AirlabsAirlineItem;
import com.example.moduleflight.repository.AirlineRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AirlineImportServiceTest {

  @Mock private AirlabsAirlinesClient airlabsAirlinesClient;
  @Mock private AirlineRepository airlineRepository;

  @Captor private ArgumentCaptor<List<AirlineEntity>> savedAirlinesCaptor;

  private AirlineImportService service;

  private static AirlineImportProperties testProperties() {
    return new AirlineImportProperties(
        true,
        "https://airlabs.co",
        "test-key",
        "0 30 3 * * *",
        20,
        2,
        false,
        "",
        true,
        "code,name");
  }

  @BeforeEach
  void setUp() {
    service = new AirlineImportService(airlabsAirlinesClient, airlineRepository, testProperties());
  }

  @Test
  void importAirlines_shouldSkipWhenApiKeyBlank() {
    AirlineImportProperties noKey =
        new AirlineImportProperties(
            true, "https://airlabs.co", "", "0 30 3 * * *", 20, 2, false, "", true, "code,name");
    AirlineImportService svc =
        new AirlineImportService(airlabsAirlinesClient, airlineRepository, noKey);

    AirlineImportService.AirlineImportSummary summary = svc.importAirlines();

    assertEquals(0, summary.fetched());
    verify(airlabsAirlinesClient, never()).fetchAirlines();
  }

  @Test
  void importAirlines_shouldInsertNewAirlines() {
    when(airlabsAirlinesClient.fetchCountryNamesByCode()).thenReturn(Map.of());
    when(airlabsAirlinesClient.fetchAirlines())
        .thenReturn(
            List.of(
                new AirlabsAirlineItem("Vietnam Airlines", "VN", "HVN", "VN"),
                new AirlabsAirlineItem("Cargo Only", null, "CGO", "US")));
    when(airlineRepository.findByCodeIn(anyCollection())).thenReturn(List.of());

    AirlineImportService.AirlineImportSummary summary = service.importAirlines();

    assertEquals(2, summary.fetched());
    assertEquals(2, summary.inserted());
    assertEquals(0, summary.updated());
    assertEquals(0, summary.skipped());

    verify(airlineRepository).saveAll(savedAirlinesCaptor.capture());
    List<AirlineEntity> saved = savedAirlinesCaptor.getValue();
    assertEquals(2, saved.size());
    AirlineEntity vn =
        saved.stream().filter(a -> "VN".equals(a.getCode())).findFirst().orElseThrow();
    assertEquals("Vietnam Airlines", vn.getName());
    assertEquals("VN", vn.getCountryCode());
    assertEquals("https://airlabs.co/img/airline/m/VN.png", vn.getLogoUrl());

    AirlineEntity icaoOnly =
        saved.stream().filter(a -> "CGO".equals(a.getCode())).findFirst().orElseThrow();
    assertEquals("Cargo Only", icaoOnly.getName());
    assertNull(icaoOnly.getLogoUrl());
  }

  @Test
  void importAirlines_shouldUpdateExisting() {
    when(airlabsAirlinesClient.fetchCountryNamesByCode()).thenReturn(Map.of());
    AirlineEntity existing = new AirlineEntity();
    existing.setCode("VN");
    existing.setName("Old Name");
    existing.setLogoUrl("https://example.com/old.png");
    existing.setCountryCode("US");
    existing.setCountry("United States");
    existing.setCity("Manual City");

    when(airlabsAirlinesClient.fetchAirlines())
        .thenReturn(List.of(new AirlabsAirlineItem("Vietnam Airlines", "VN", "HVN", "VN")));
    when(airlineRepository.findByCodeIn(Set.of("VN"))).thenReturn(List.of(existing));

    AirlineImportService.AirlineImportSummary summary = service.importAirlines();

    assertEquals(1, summary.updated());
    assertEquals(0, summary.inserted());
    assertEquals("Vietnam Airlines", existing.getName());
    assertEquals("VN", existing.getCountryCode());
    assertEquals("https://airlabs.co/img/airline/m/VN.png", existing.getLogoUrl());
    assertEquals("Manual City", existing.getCity());
  }

  @Test
  void importAirlines_shouldSkipInvalidRows() {
    when(airlabsAirlinesClient.fetchCountryNamesByCode()).thenReturn(Map.of());
    when(airlabsAirlinesClient.fetchAirlines())
        .thenReturn(
            List.of(
                new AirlabsAirlineItem("", "XX", "XXX", "US"),
                new AirlabsAirlineItem("No Code", null, null, null),
                new AirlabsAirlineItem("Valid", "AB", "ABC", null)));
    when(airlineRepository.findByCodeIn(anyCollection())).thenReturn(List.of());

    AirlineImportService.AirlineImportSummary summary = service.importAirlines();

    assertEquals(3, summary.fetched());
    assertEquals(2, summary.skipped());
    assertEquals(1, summary.inserted());
  }

  @Test
  void importAirlines_shouldRespectRequireIata() {
    AirlineImportProperties requireIata =
        new AirlineImportProperties(
            true, "https://airlabs.co", "key", "0 30 3 * * *", 20, 2, true, "", true, "code,name");
    AirlineImportService svc =
        new AirlineImportService(airlabsAirlinesClient, airlineRepository, requireIata);

    when(airlabsAirlinesClient.fetchAirlines())
        .thenReturn(List.of(new AirlabsAirlineItem("Icao Only", null, "ABC", "GB")));
    when(airlabsAirlinesClient.fetchCountryNamesByCode()).thenReturn(Map.of());
    AirlineImportService.AirlineImportSummary summary = svc.importAirlines();

    assertEquals(1, summary.skipped());
    assertEquals(0, summary.inserted());
  }

  @Test
  void toCandidate_shouldPreferCountriesDbName() {
    AirlineImportService svc =
        new AirlineImportService(airlabsAirlinesClient, airlineRepository, testProperties());

    var c =
        svc.toCandidate(
            new AirlabsAirlineItem("Test Air", "ta", "TST", "de"),
            Map.of("DE", "Germany (AirLabs)"));

    assertEquals("TA", c.code());
    assertEquals("Test Air", c.name());
    assertEquals("DE", c.countryCode());
    assertEquals("Germany (AirLabs)", c.country());
  }

  @Test
  void toCandidate_shouldFallbackToLocaleWhenNotInCountriesMap() {
    AirlineImportService svc =
        new AirlineImportService(airlabsAirlinesClient, airlineRepository, testProperties());

    var c = svc.toCandidate(new AirlabsAirlineItem("Test Air", "ta", "TST", "de"), Map.of());

    assertEquals("TA", c.code());
    assertEquals("Germany", c.country());
  }
}
