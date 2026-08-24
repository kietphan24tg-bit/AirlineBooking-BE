package com.example.moduleai.tool;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.AgentToolPolicy;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolResult;
import com.example.moduleflight.application.AirportMarketContext;
import com.example.moduleflight.application.AirportPickerItem;
import com.example.moduleflight.application.AirportPickerResult;
import com.example.moduleflight.application.AirportPickerService;
import com.example.moduleflight.application.AirportQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class AirportTools extends AbstractAgentTool {

  private final AirportPickerService airportPickerService;
  private final AirportQueryService airportQueryService;

  public AirportTools(
      ToolExecutionGuard guard,
      AirportPickerService airportPickerService,
      AirportQueryService airportQueryService) {
    super(guard);
    this.airportPickerService = airportPickerService;
    this.airportQueryService = airportQueryService;
  }

  @Tool(
      description =
          "Get airport picker data including recent, suggested, and initial airport items.")
  @AgentToolPolicy(
      tool = "get_airport_picker",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<AirportPickerResult> get_airport_picker(
      AirportPickerInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "get_airport_picker",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        context,
        () ->
            airportPickerService.picker(
                context.userId(),
                input.acceptLanguage(),
                input.timezone(),
                input.siteLocale(),
                input.geoIpCountry(),
                input.recentLimit(),
                input.suggestedLimit(),
                input.initialLimit()));
  }

  @Tool(description = "Search airports by code, city, airport name, or country.")
  @AgentToolPolicy(
      tool = "search_airports",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<java.util.List<AirportPickerItem>> search_airports(
      SearchAirportsInput input, ToolContext toolContext) {
    return execute(
        "search_airports",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        AgentToolContext.from(toolContext),
        () -> airportPickerService.search(input.query(), input.limit()));
  }

  @Tool(
      description =
          "List airports grouped by detected market using trusted backend market hints when available.")
  @AgentToolPolicy(
      tool = "list_airports",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<
          java.util.Map<
              String, java.util.List<com.example.moduleflight.application.AirportGroupedItem>>>
      list_airports(ListAirportsInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "list_airports",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        context,
        () ->
            airportQueryService.findGroupedByDetectedMarket(
                new AirportMarketContext(
                    context.userId(),
                    input.acceptLanguage(),
                    input.timezone(),
                    input.siteLocale(),
                    input.geoIpCountry())));
  }

  @Tool(
      description =
          "Group airports by a country filter. Pass an empty filter to list all grouped countries.")
  @AgentToolPolicy(
      tool = "group_airports_by_country",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<
          java.util.Map<
              String, java.util.List<com.example.moduleflight.application.AirportGroupedItem>>>
      group_airports_by_country(GroupAirportsByCountryInput input, ToolContext toolContext) {
    return execute(
        "group_airports_by_country",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        AgentToolContext.from(toolContext),
        () -> airportQueryService.findGroupedByCountry(input.countryFilter()));
  }

  public record AirportPickerInput(
      String acceptLanguage,
      String timezone,
      String siteLocale,
      String geoIpCountry,
      @Min(1) @Max(10) Integer recentLimit,
      @Min(1) @Max(10) Integer suggestedLimit,
      @Min(1) @Max(12) Integer initialLimit) {}

  public record SearchAirportsInput(String query, @Min(1) @Max(20) Integer limit) {}

  public record ListAirportsInput(
      String acceptLanguage, String timezone, String siteLocale, String geoIpCountry) {}

  public record GroupAirportsByCountryInput(String countryFilter) {}
}
