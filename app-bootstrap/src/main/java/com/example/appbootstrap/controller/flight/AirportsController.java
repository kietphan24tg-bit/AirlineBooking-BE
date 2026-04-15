package com.example.appbootstrap.controller.flight;

import com.example.appbootstrap.controller.flight.dto.AirportByCountryItemResponse;
import com.example.moduleflight.application.AirportGroupedItem;
import com.example.moduleflight.application.AirportQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/airports")
@Tag(name = "Airports", description = "Airport reference data")
public class AirportsController {

  private final AirportQueryService airportQueryService;

  public AirportsController(AirportQueryService airportQueryService) {
    this.airportQueryService = airportQueryService;
  }

  @GetMapping
  @Operation(
      summary = "List all airports grouped by country",
      description = "Returns all airports as a map of country name to airport items.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Grouped airports returned",
        content = @Content)
  })
  Map<String, List<AirportByCountryItemResponse>> groupedAll() {
    return toResponse(airportQueryService.findGroupedByCountry(null));
  }

  @GetMapping("/grouped-by-country")
  @Operation(
      summary = "List airports grouped by country",
      description =
          "Legacy-compatible grouped endpoint. Returns airports as a map of country name to"
              + " airport items. Optional query `country` filters to a single country (exact"
              + " match on stored country value).")
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
