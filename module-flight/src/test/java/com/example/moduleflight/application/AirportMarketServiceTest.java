package com.example.moduleflight.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.moduleflight.config.AirportMarketProperties;
import com.example.moduleflight.entity.AirportEntity;
import com.example.moduleflight.entity.UserRecentAirportEntity;
import com.example.moduleflight.repository.UserRecentAirportRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AirportMarketServiceTest {

  @Mock private UserRecentAirportRepository userRecentAirportRepository;
  @Mock private UserMarketProfileProvider userMarketProfileProvider;

  private AirportMarketService service;

  @BeforeEach
  void setUp() {
    service =
        new AirportMarketService(
            userRecentAirportRepository,
            new AirportMarketProperties("VN", null, null),
            List.of(userMarketProfileProvider));
  }

  @Test
  void detectMarket_recentBehaviorBeatsNationalityGeoIpAndBrowserHeaders() {
    when(userRecentAirportRepository.findByUserIdAndUsageTypeInOrderByLastUsedAtDesc(
            any(), any(), any(Pageable.class)))
        .thenReturn(List.of(recent("KR", 2), recent("KR", 1)));
    when(userMarketProfileProvider.findByUserId(7L))
        .thenReturn(Optional.of(new UserMarketProfile("US")));

    String market =
        service.detectMarket(
            new AirportMarketContext(7L, "vi-VN,vi;q=0.9", "Asia/Ho_Chi_Minh", "VN", "CA"));

    assertEquals("KR", market);
  }

  @Test
  void detectMarket_consensusCanBeatNationality() {
    when(userRecentAirportRepository.findByUserIdAndUsageTypeInOrderByLastUsedAtDesc(
            any(), any(), any(Pageable.class)))
        .thenReturn(List.of());
    when(userMarketProfileProvider.findByUserId(7L))
        .thenReturn(Optional.of(new UserMarketProfile("VN")));

    String market =
        service.detectMarket(
            new AirportMarketContext(7L, "en-SG,en;q=0.9", "Asia/Singapore", null, "SG"));

    assertEquals("SG", market);
  }

  @Test
  void detectMarket_nationalityBeatsSingleGeoIpSignal() {
    when(userRecentAirportRepository.findByUserIdAndUsageTypeInOrderByLastUsedAtDesc(
            any(), any(), any(Pageable.class)))
        .thenReturn(List.of());
    when(userMarketProfileProvider.findByUserId(7L))
        .thenReturn(Optional.of(new UserMarketProfile("JP")));

    String market = service.detectMarket(new AirportMarketContext(7L, null, null, null, "CA"));

    assertEquals("JP", market);
  }

  @Test
  void detectMarket_recentBehaviorUsesDiminishingReturnsAndCap() {
    when(userRecentAirportRepository.findByUserIdAndUsageTypeInOrderByLastUsedAtDesc(
            any(), any(), any(Pageable.class)))
        .thenReturn(
            List.of(
                recent("JP", 10),
                recent("JP", 10),
                recent("JP", 10),
                recent("JP", 10),
                recent("JP", 10)));
    when(userMarketProfileProvider.findByUserId(7L)).thenReturn(Optional.empty());

    AirportMarketSignal recentSignal =
        service.buildSignals(new AirportMarketContext(7L, null, null, null, null)).stream()
            .filter(signal -> signal.source() == AirportMarketSignalSource.RECENT_BEHAVIOR)
            .findFirst()
            .orElseThrow();

    assertEquals("JP", recentSignal.market());
    assertEquals(150, recentSignal.score());
  }

  @Test
  void detectMarket_geoIpHandlesAnonymousEnglishOnlyUser() {
    String market = service.detectMarket(new AirportMarketContext(null, "en", null, null, "CA"));

    assertEquals("CA", market);
  }

  @Test
  void detectMarket_englishOnlyDoesNotInferUnitedStates() {
    List<AirportMarketSignal> signals =
        service.buildSignals(new AirportMarketContext(null, "en", null, null, null));

    assertFalse(signals.stream().anyMatch(signal -> signal.market().equals("US")));
    assertEquals(
        "VN", service.detectMarket(new AirportMarketContext(null, "en", null, null, null)));
  }

  @Test
  void detectMarket_acceptLanguageRegionMapsToRegion() {
    String market =
        service.detectMarket(new AirportMarketContext(null, "en-CA,en;q=0.9", null, null, null));

    assertEquals("CA", market);
  }

  @Test
  void detectMarket_timezoneBeatsAcceptLanguageRegion() {
    String market =
        service.detectMarket(
            new AirportMarketContext(null, "zh-HK,zh;q=0.9", "Asia/Singapore", null, null));

    assertEquals("SG", market);
  }

  @Test
  void detectMarket_englishUnitedStatesDoesNotBeatBangkokTimezone() {
    String market =
        service.detectMarket(
            new AirportMarketContext(null, "en-US,en;q=0.9", "Asia/Bangkok", null, null));

    assertEquals("TH", market);
  }

  @Test
  void detectMarket_siteLocaleVietnamBeatsEnglishUnitedStatesAndBangkokTimezone() {
    String market =
        service.detectMarket(
            new AirportMarketContext(null, "en-US,en;q=0.9", "Asia/Bangkok", "vi-VN", null));

    assertEquals("VN", market);
  }

  @Test
  void detectMarket_geoIpVietnamBeatsEnglishUnitedStates() {
    String market =
        service.detectMarket(new AirportMarketContext(null, "en-US,en;q=0.9", null, null, "VN"));

    assertEquals("VN", market);
  }

  @Test
  void detectMarket_geoIpScoreIsConfigurable() {
    AirportMarketService lowGeoIpScore =
        new AirportMarketService(
            userRecentAirportRepository,
            new AirportMarketProperties("VN", null, 20),
            List.of(userMarketProfileProvider));

    String market =
        lowGeoIpScore.detectMarket(new AirportMarketContext(null, null, null, "vi-VN", "US"));

    assertEquals("VN", market);
  }

  @Test
  void detectMarket_timezoneWorksForConfiguredZone() {
    String market =
        service.detectMarket(new AirportMarketContext(null, null, "Europe/Zurich", null, null));

    assertEquals("CH", market);
  }

  @Test
  void detectMarket_missingTimezoneMappingEmitsNoSignalAndUsesDefault() {
    List<AirportMarketSignal> signals =
        service.buildSignals(new AirportMarketContext(null, null, "Antarctica/Troll", null, null));

    assertFalse(
        signals.stream().anyMatch(signal -> signal.source() == AirportMarketSignalSource.TIMEZONE));
    assertEquals(
        "VN",
        service.detectMarket(new AirportMarketContext(null, null, "Antarctica/Troll", null, null)));
  }

  @Test
  void detectMarket_usesConfiguredDefaultMarket() {
    AirportMarketService customDefault =
        new AirportMarketService(
            userRecentAirportRepository,
            new AirportMarketProperties("SG", null, null),
            List.of(userMarketProfileProvider));

    String market =
        customDefault.detectMarket(new AirportMarketContext(null, null, null, null, null));

    assertEquals("SG", market);
  }

  @Test
  void detectMarket_ignoresMalformedGeoIpCountry() {
    String market = service.detectMarket(new AirportMarketContext(null, null, null, null, "XX"));

    assertEquals("VN", market);
  }

  private static UserRecentAirportEntity recent(String countryCode, int searchCount) {
    AirportEntity airport = new AirportEntity();
    airport.setCountryCode(countryCode);
    UserRecentAirportEntity recent = new UserRecentAirportEntity();
    recent.setAirport(airport);
    recent.setUsageType(AirportUsageType.DEPARTURE);
    recent.setSearchCount(searchCount);
    return recent;
  }
}
