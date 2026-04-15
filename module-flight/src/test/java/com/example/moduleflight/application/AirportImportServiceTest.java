package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleflight.config.AirportImportProperties;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.integration.airportsapi.AirportsApiClient;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.AirportAttributes;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.AirportData;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.AirportRelationships;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.IncludedAttributes;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.IncludedResource;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.PageLinks;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.PageMeta;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.RelationshipData;
import com.example.moduleflight.integration.airportsapi.dto.AirportsApiPageResponse.RelationshipRef;
import com.example.moduleflight.repository.AirportRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class AirportImportServiceTest {

  @Mock private AirportsApiClient airportsApiClient;
  @Mock private AirportRepository airportRepository;
  @Mock private PlatformTransactionManager transactionManager;

  private AirportImportService service;

  @BeforeEach
  void setUp() {
    AirportImportProperties properties =
        new AirportImportProperties(
            true,
            "https://airportsapi.com",
            "0 0 3 * * *",
            100,
            20,
            2,
            "medium_airport,large_airport",
            true,
            "country,region",
            "UNKNOWN",
            "UNKNOWN",
            "UTC");
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    service =
        new AirportImportService(
            airportsApiClient,
            airportRepository,
            properties,
            transactionManager);
  }

  @Test
  void importAirports_shouldNormalizeFallbackAndUpsert() {
    AirportsApiPageResponse pageOne =
        new AirportsApiPageResponse(
            List.of(
                new AirportData(
                    "1",
                    "airports",
                    new AirportAttributes(
                        "Noi Bai", "VVNB", "large_airport", "HAN", null, null, null, null),
                    null),
                new AirportData(
                    "2",
                    "airports",
                    new AirportAttributes(
                        "Tan Son Nhat",
                        "VVTS",
                        "medium_airport",
                        null,
                        "VVTS",
                        "HCM",
                        "Vietnam",
                        "Asia/Ho_Chi_Minh"),
                    null)),
            null,
            new PageMeta("https://airportsapi.com/api/airports", "cursor-2", 30),
            null,
            null);
    AirportsApiPageResponse pageTwo =
        new AirportsApiPageResponse(
            List.of(
                new AirportData(
                    "3",
                    "airports",
                    new AirportAttributes(
                        "Noi Bai International",
                        "VVNB",
                        "large_airport",
                        "HAN",
                        null,
                        "Ha Noi",
                        "Vietnam",
                        "Asia/Ho_Chi_Minh"),
                    null),
                new AirportData(
                    "4",
                    "airports",
                    new AirportAttributes(
                        "", "XXXX", "medium_airport", null, null, "X", "Y", "UTC"),
                    null)),
            null,
            null,
            null,
            null);

    AirportEntity existingHan = new AirportEntity();
    existingHan.setId(100L);
    existingHan.setCode("HAN");
    existingHan.setName("Old Name");
    existingHan.setCity("Old City");
    existingHan.setCountry("Old Country");
    existingHan.setTimeZone("Old/TZ");
    existingHan.setCreatedAt(OffsetDateTime.now().minusDays(3));
    existingHan.setUpdatedAt(OffsetDateTime.now().minusDays(2));

    when(airportsApiClient.fetchPage(null)).thenReturn(pageOne);
    when(airportsApiClient.fetchPage("cursor-2")).thenReturn(pageTwo);
    when(airportRepository.findByCodeIn(anyCollection()))
        .thenReturn(List.of(), List.of(existingHan));
    when(airportRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    AirportImportService.AirportImportSummary summary = service.importAirports();

    assertEquals(4, summary.fetched());
    assertEquals(1, summary.inserted());
    assertEquals(1, summary.updated());
    assertEquals(2, summary.skipped());
    assertEquals(0, summary.failed());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<AirportEntity>> saveCaptor =
        (ArgumentCaptor<List<AirportEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
    verify(airportRepository, times(2)).saveAll(saveCaptor.capture());
    List<List<AirportEntity>> allSaves = saveCaptor.getAllValues();

    List<AirportEntity> firstSave = allSaves.get(0);
    AirportEntity hanNew =
        firstSave.stream().filter(item -> "HAN".equals(item.getCode())).findFirst().orElseThrow();
    assertEquals("UNKNOWN", hanNew.getCity());
    assertEquals("UNKNOWN", hanNew.getCountry());
    assertEquals("UTC", hanNew.getTimeZone());

    List<AirportEntity> secondSave = allSaves.get(1);
    AirportEntity hanUpdated =
        secondSave.stream().filter(item -> "HAN".equals(item.getCode())).findFirst().orElseThrow();
    assertEquals("Noi Bai International", hanUpdated.getName());
    assertEquals("Ha Noi", hanUpdated.getCity());
    assertEquals("Vietnam", hanUpdated.getCountry());
    assertEquals("Asia/Ho_Chi_Minh", hanUpdated.getTimeZone());
  }

  @Test
  void importAirports_shouldReadCursorFromLinksNext() {
    AirportsApiPageResponse pageOne =
        new AirportsApiPageResponse(
            List.of(),
            new PageLinks("https://airportsapi.com/api/airports?page%5Bcursor%5D=abc123"),
            null,
            null,
            null);
    AirportsApiPageResponse pageTwo =
        new AirportsApiPageResponse(List.of(), null, null, null, null);

    when(airportsApiClient.fetchPage(null)).thenReturn(pageOne);
    when(airportsApiClient.fetchPage("abc123")).thenReturn(pageTwo);

    AirportImportService.AirportImportSummary summary = service.importAirports();

    assertEquals(0, summary.fetched());
    verify(airportsApiClient).fetchPage("abc123");
  }

  @Test
  void importAirports_shouldUseIncludedCountryAndRegionData() {
    AirportData airportData =
        new AirportData(
            "5",
            "airports",
            new AirportAttributes(
                "Los Angeles International",
                "KLAX",
                "large_airport",
                "LAX",
                "KLAX",
                null,
                null,
                "America/Los_Angeles"),
            new AirportRelationships(
                new RelationshipRef(new RelationshipData("countries", "US")),
                new RelationshipRef(new RelationshipData("regions", "US-CA"))));
    AirportsApiPageResponse page =
        new AirportsApiPageResponse(
            List.of(airportData),
            null,
            null,
            List.of(
                new IncludedResource(
                    "US", "countries", new IncludedAttributes("United States", "US", "NA")),
                new IncludedResource(
                    "US-CA", "regions", new IncludedAttributes("California", "US-CA", null))),
            null);

    when(airportsApiClient.fetchPage(null)).thenReturn(page);
    when(airportRepository.findByCodeIn(anyCollection())).thenReturn(List.of());
    when(airportRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    AirportImportService.AirportImportSummary summary = service.importAirports();

    assertEquals(1, summary.inserted());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<AirportEntity>> saveCaptor =
        (ArgumentCaptor<List<AirportEntity>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
    verify(airportRepository).saveAll(saveCaptor.capture());
    AirportEntity saved = saveCaptor.getValue().get(0);
    assertEquals("United States", saved.getCountry());
    assertEquals("California", saved.getCity());
  }

  @Test
  void importAirports_shouldSkipFilteredAirportTypes() {
    AirportsApiPageResponse page =
        new AirportsApiPageResponse(
            List.of(
                new AirportData(
                    "6",
                    "airports",
                    new AirportAttributes(
                        "Helipad", "00A", "heliport", "AAA", null, "X", "Y", "UTC"),
                    null)),
            null,
            null,
            null,
            null);

    when(airportsApiClient.fetchPage(null)).thenReturn(page);

    AirportImportService.AirportImportSummary summary = service.importAirports();

    assertEquals(1, summary.skipped());
    verify(airportRepository, times(0)).saveAll(any());
  }
}
