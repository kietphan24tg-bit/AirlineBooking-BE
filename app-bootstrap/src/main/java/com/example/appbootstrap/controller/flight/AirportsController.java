package com.example.appbootstrap.controller.flight;

import com.example.appbootstrap.application.flight.AirportGeoIpCountryResolver;
import com.example.appbootstrap.controller.flight.dto.AirportByCountryItemResponse;
import com.example.appbootstrap.controller.flight.dto.AirportPickerResponse;
import com.example.moduleflight.application.AirportGroupedItem;
import com.example.moduleflight.application.AirportMarketContext;
import com.example.moduleflight.application.AirportPickerService;
import com.example.moduleflight.application.AirportQueryService;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/airports")
@Tag(name = "Airports", description = "Airport reference data")
public class AirportsController {

  private final AirportQueryService airportQueryService;
  private final AirportPickerService airportPickerService;
  private final AirportGeoIpCountryResolver airportGeoIpCountryResolver;

  public AirportsController(
      AirportQueryService airportQueryService,
      AirportPickerService airportPickerService,
      AirportGeoIpCountryResolver airportGeoIpCountryResolver) {
    this.airportQueryService = airportQueryService;
    this.airportPickerService = airportPickerService;
    this.airportGeoIpCountryResolver = airportGeoIpCountryResolver;
  }

  @GetMapping
  @Operation(
      summary = "List all airports grouped by country",
      description = "Returns all airports as a map of country name to airport items.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Grouped airports returned",
        content = @Content)
  })
  Map<String, List<AirportByCountryItemResponse>> groupedAll(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
      @RequestHeader(name = "X-Timezone", required = false) String timezone,
      @RequestHeader(name = "X-Site-Locale", required = false) String siteLocale) {
    Long userId = principal == null ? null : principal.getUserId();
    return toResponse(
        airportQueryService.findGroupedByDetectedMarket(
            new AirportMarketContext(
                userId,
                acceptLanguage,
                timezone,
                siteLocale,
                airportGeoIpCountryResolver.resolveCountry())));
  }

  @GetMapping("/picker")
  @Operation(
      summary = "Initial airport picker suggestions",
      description =
          "Returns recent airports for authenticated users and local market suggestions detected"
              + " from saved usage, Accept-Language, X-Timezone, X-Site-Locale, or fallback VN.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Airport picker data returned",
        content = @Content(schema = @Schema(implementation = AirportPickerResponse.class)))
  })
  AirportPickerResponse picker(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
      @RequestHeader(name = "X-Timezone", required = false) String timezone,
      @RequestHeader(name = "X-Site-Locale", required = false) String siteLocale,
      @Parameter(description = "Initial render item count, clamped to 8-12")
          @RequestParam(required = false)
          Integer limit,
      @Parameter(description = "Recent item count for authenticated users, clamped to 3-5")
          @RequestParam(required = false)
          Integer recentLimit,
      @Parameter(description = "Suggested item count, clamped to 6-10")
          @RequestParam(required = false)
          Integer suggestedLimit) {
    Long userId = principal == null ? null : principal.getUserId();
    return AirportPickerResponse.from(
        airportPickerService.picker(
            userId,
            acceptLanguage,
            timezone,
            siteLocale,
            airportGeoIpCountryResolver.resolveCountry(),
            recentLimit,
            suggestedLimit,
            limit));
  }

  @GetMapping("/search")
  @Operation(
      summary = "Search airports",
      description =
          "Returns airport autocomplete results ranked by exact code, code prefix, city prefix,"
              + " name contains, then country contains.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Airport search results returned",
        content = @Content)
  })
  List<AirportPickerResponse.AirportPickerItemResponse> search(
      @Parameter(description = "Airport search text")
          @RequestParam(name = "query", required = false)
          String query,
      @Parameter(description = "Result count, clamped to 1-20 with default 10")
          @RequestParam(required = false)
          Integer limit) {
    return airportPickerService.search(query, limit).stream()
        .map(AirportPickerResponse.AirportPickerItemResponse::from)
        .toList();
  }

  @GetMapping("/grouped-by-country")
  @Operation(
      summary = "List airports grouped by country",
      description =
          "Legacy-compatible grouped endpoint. Returns airports as a map of country name to"
              + " airport items. Optional query `country` filters to a single country (exact"
              + " match on stored country value).")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Grouped airports returned",
        content = @Content)
  })
  Map<String, List<AirportByCountryItemResponse>> groupedByCountry(
      @Parameter(description = "Exact country string as stored in database")
          @RequestParam(required = false)
          String country) {
    return toResponse(airportQueryService.findGroupedByCountry(country));
  }

  private Map<String, List<AirportByCountryItemResponse>> toResponse(
      Map<String, List<AirportGroupedItem>> raw) {
    Map<String, List<AirportByCountryItemResponse>> result = new LinkedHashMap<>();
    raw.forEach(
        (key, items) ->
            result.put(key, items.stream().map(AirportByCountryItemResponse::from).toList()));
    return result;
  }
}
