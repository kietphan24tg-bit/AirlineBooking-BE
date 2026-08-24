package com.example.moduleweb.controller.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.moduleflight.application.AirportGroupedItem;
import com.example.moduleflight.application.AirportPickerInitialItem;
import com.example.moduleflight.application.AirportPickerItem;
import com.example.moduleflight.application.AirportPickerResult;
import com.example.moduleflight.application.AirportPickerService;
import com.example.moduleflight.application.AirportQueryService;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.moduleidentity.entity.Role;
import com.example.moduleweb.application.flight.AirportGeoIpCountryResolver;
import com.example.moduleweb.controller.flight.dto.AirportByCountryItemResponse;
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
  @Mock private AirportPickerService airportPickerService;
  @Mock private AirportGeoIpCountryResolver airportGeoIpCountryResolver;

  private AirportsController controller;

  @BeforeEach
  void setUp() {
    controller =
        new AirportsController(
            airportQueryService, airportPickerService, airportGeoIpCountryResolver);
  }

  @Test
  void groupedAll_passesPrincipalHeadersAndMapsToResponseDtos() {
    Map<String, List<AirportGroupedItem>> raw = new LinkedHashMap<>();
    raw.put(
        "Thailand", List.of(new AirportGroupedItem(1L, "BKK", "Suvarnabhumi", "Bangkok", "TH")));
    when(airportGeoIpCountryResolver.resolveCountry()).thenReturn("TH");
    when(airportQueryService.findGroupedByDetectedMarket(any())).thenReturn(raw);

    Map<String, List<AirportByCountryItemResponse>> result =
        controller.groupedAll(
            new AppUserPrincipal(7L, "user@example.com", "secret", Role.USER, null),
            "th-TH,th;q=0.9",
            "Asia/Bangkok",
            "TH");

    verify(airportQueryService)
        .findGroupedByDetectedMarket(
            new com.example.moduleflight.application.AirportMarketContext(
                7L, "th-TH,th;q=0.9", "Asia/Bangkok", "TH", "TH"));
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

  @Test
  void picker_mapsHeadersPrincipalAndResponse() {
    AirportPickerResult raw =
        new AirportPickerResult(
            "VN",
            List.of(
                new AirportPickerItem(
                    1L, "SGN", "Tan Son Nhat", "Ho Chi Minh City", "Vietnam", "VN")),
            List.of(new AirportPickerItem(2L, "HAN", "Noi Bai", "Hanoi", "Vietnam", "VN")),
            List.of(
                new AirportPickerInitialItem(
                    1L, "SGN", "Tan Son Nhat", "Ho Chi Minh City", "Vietnam", "VN", "RECENT")));
    when(airportGeoIpCountryResolver.resolveCountry()).thenReturn("VN");
    when(airportPickerService.picker(
            7L, "vi-VN,vi;q=0.9", "Asia/Ho_Chi_Minh", "VN", "VN", 5, 10, 10))
        .thenReturn(raw);

    var response =
        controller.picker(
            new AppUserPrincipal(7L, "user@example.com", "secret", Role.USER, null),
            "vi-VN,vi;q=0.9",
            "Asia/Ho_Chi_Minh",
            "VN",
            10,
            5,
            10);

    assertEquals("VN", response.market());
    assertEquals("SGN", response.recent().getFirst().code());
    assertEquals("VN", response.recent().getFirst().countryCode());
    assertEquals("HAN", response.suggested().getFirst().code());
    assertEquals("VN", response.suggested().getFirst().countryCode());
    assertEquals("RECENT", response.initialItems().getFirst().source());
    assertEquals("VN", response.initialItems().getFirst().countryCode());
    verify(airportPickerService)
        .picker(7L, "vi-VN,vi;q=0.9", "Asia/Ho_Chi_Minh", "VN", "VN", 5, 10, 10);
  }

  @Test
  void search_passesQueryLimitAndMapsResponse() {
    when(airportPickerService.search("sgn", 7))
        .thenReturn(
            List.of(
                new AirportPickerItem(
                    1L, "SGN", "Tan Son Nhat", "Ho Chi Minh City", "Vietnam", "VN")));

    var response = controller.search("sgn", 7);

    verify(airportPickerService).search("sgn", 7);
    assertEquals(1, response.size());
    assertEquals(1L, response.getFirst().id());
    assertEquals("SGN", response.getFirst().code());
    assertEquals("Tan Son Nhat", response.getFirst().name());
    assertEquals("Ho Chi Minh City", response.getFirst().city());
    assertEquals("Vietnam", response.getFirst().country());
    assertEquals("VN", response.getFirst().countryCode());
  }
}
