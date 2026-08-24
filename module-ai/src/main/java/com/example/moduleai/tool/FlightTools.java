package com.example.moduleai.tool;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.AgentToolPolicy;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolResult;
import com.example.moduleai.tool.support.PromotionCatalogToolService;
import com.example.modulebooking.application.BookingRevalidateService;
import com.example.modulebooking.application.PassengerSummaryCommand;
import com.example.modulebooking.application.RevalidateFareCommand;
import com.example.modulebooking.application.RevalidateFareView;
import com.example.modulebooking.application.RevalidateItineraryCommand;
import com.example.modulebooking.application.RevalidateItineraryView;
import com.example.moduleflight.application.CabinClass;
import com.example.moduleflight.application.FareFamilyCatalogService;
import com.example.moduleflight.application.FlightCalendarSearchQuery;
import com.example.moduleflight.application.FlightCalendarSearchResult;
import com.example.moduleflight.application.FlightCalendarSearchService;
import com.example.moduleflight.application.FlightConnectionFilter;
import com.example.moduleflight.application.FlightSearchFacadeService;
import com.example.moduleflight.application.FlightSearchQuery;
import com.example.moduleflight.application.FlightSearchResult;
import com.example.moduleflight.application.FlightSearchTripType;
import com.example.moduleflight.application.FlightSortBy;
import com.example.moduleflight.application.TimeOfDayBucket;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class FlightTools extends AbstractAgentTool {

  private final FlightSearchFacadeService flightSearchFacadeService;
  private final FlightCalendarSearchService flightCalendarSearchService;
  private final FareFamilyCatalogService fareFamilyCatalogService;
  private final PromotionCatalogToolService promotionCatalogToolService;
  private final BookingRevalidateService bookingRevalidateService;

  public FlightTools(
      ToolExecutionGuard guard,
      FlightSearchFacadeService flightSearchFacadeService,
      FlightCalendarSearchService flightCalendarSearchService,
      FareFamilyCatalogService fareFamilyCatalogService,
      PromotionCatalogToolService promotionCatalogToolService,
      BookingRevalidateService bookingRevalidateService) {
    super(guard);
    this.flightSearchFacadeService = flightSearchFacadeService;
    this.flightCalendarSearchService = flightCalendarSearchService;
    this.fareFamilyCatalogService = fareFamilyCatalogService;
    this.promotionCatalogToolService = promotionCatalogToolService;
    this.bookingRevalidateService = bookingRevalidateService;
  }

  @Tool(description = "Search flights for a one-way or round-trip itinerary with filters.")
  @AgentToolPolicy(
      tool = "search_flights",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<FlightSearchResult> search_flights(
      SearchFlightsInput input, ToolContext toolContext) {
    return execute(
        "search_flights",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        AgentToolContext.from(toolContext),
        () ->
            flightSearchFacadeService.search(
                new FlightSearchQuery(
                    input.tripType(),
                    input.fromAirportCode(),
                    input.toAirportCode(),
                    input.departureDate(),
                    input.returnDate(),
                    input.adult(),
                    input.child(),
                    input.infant(),
                    input.promoCode(),
                    input.cursor(),
                    input.outboundCursor(),
                    input.inboundCursor(),
                    input.limit(),
                    input.cabinClasses(),
                    input.maxBudget(),
                    input.departureTimeBucket(),
                    input.arrivalTimeBucket(),
                    input.connectionFilter(),
                    input.sortBy())));
  }

  @Tool(
      description =
          "Search a date window around a target day to compare flight prices and availability.")
  @AgentToolPolicy(
      tool = "search_flight_calendar",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<FlightCalendarSearchResult> search_flight_calendar(
      SearchFlightCalendarInput input, ToolContext toolContext) {
    return execute(
        "search_flight_calendar",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        AgentToolContext.from(toolContext),
        () ->
            flightCalendarSearchService.search(
                new FlightCalendarSearchQuery(
                    input.tripType(),
                    input.fromAirportCode(),
                    input.toAirportCode(),
                    input.centerDate(),
                    input.adult(),
                    input.child(),
                    input.infant(),
                    input.promoCode(),
                    input.limit(),
                    input.cabinClasses(),
                    input.maxBudget(),
                    input.departureTimeBucket(),
                    input.arrivalTimeBucket(),
                    input.connectionFilter(),
                    input.sortBy(),
                    input.daysBefore(),
                    input.daysAfter())));
  }

  @Tool(description = "List fare family benefits and reference prices by market and currency.")
  @AgentToolPolicy(
      tool = "list_fare_families",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<java.util.List<FareFamilyCatalogService.FareFamilyView>> list_fare_families(
      FareFamiliesInput input, ToolContext toolContext) {
    return execute(
        "list_fare_families",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        AgentToolContext.from(toolContext),
        () -> fareFamilyCatalogService.listFareFamilies(input.countryCode(), input.currency()));
  }

  @Tool(
      description =
          "Revalidate one selected fare before booking and return an expiring revalidate session.")
  @AgentToolPolicy(
      tool = "revalidate_fare",
      operation = ToolOperation.PREVIEW,
      access = ToolAccess.PUBLIC)
  public ToolResult<RevalidateFareOutput> revalidate_fare(
      RevalidateFareInput input, ToolContext toolContext) {
    return execute(
        "revalidate_fare",
        ToolOperation.PREVIEW,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        AgentToolContext.from(toolContext),
        () ->
            RevalidateFareOutput.from(
                bookingRevalidateService.revalidate(
                    new RevalidateFareCommand(
                        input.flightId(),
                        input.fareId(),
                        input.promoCode(),
                        new PassengerSummaryCommand(
                            input.passengerSummary().adult(),
                            input.passengerSummary().child(),
                            input.passengerSummary().infant())))));
  }

  @Tool(
      description =
          "Revalidate a one-way or round-trip itinerary before booking and return an expiring revalidate session.")
  @AgentToolPolicy(
      tool = "revalidate_itinerary",
      operation = ToolOperation.PREVIEW,
      access = ToolAccess.PUBLIC)
  public ToolResult<RevalidateItineraryOutput> revalidate_itinerary(
      RevalidateItineraryInput input, ToolContext toolContext) {
    return execute(
        "revalidate_itinerary",
        ToolOperation.PREVIEW,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        AgentToolContext.from(toolContext),
        () ->
            RevalidateItineraryOutput.from(
                bookingRevalidateService.revalidateItinerary(
                    new RevalidateItineraryCommand(
                        input.tripType(),
                        input.promoCode(),
                        new PassengerSummaryCommand(
                            input.passengerSummary().adult(),
                            input.passengerSummary().child(),
                            input.passengerSummary().infant()),
                        input.segments().stream()
                            .map(
                                segment ->
                                    new RevalidateItineraryCommand.SegmentCommand(
                                        segment.direction(), segment.flightId(), segment.fareId()))
                            .toList()))));
  }

  @Tool(description = "List active promotions that can be shown during flight shopping.")
  @AgentToolPolicy(
      tool = "list_promotions",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<java.util.List<PromotionCatalogToolService.PromotionCard>> list_promotions(
      EmptyInput input, ToolContext toolContext) {
    return execute(
        "list_promotions",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        AgentToolContext.from(toolContext),
        promotionCatalogToolService::getActivePromotions);
  }

  private ToolResult<UnavailableToolData> unavailable(String tool) {
    return ToolResult.failure(
        tool,
        com.example.moduleai.tool.core.ToolError.unavailable(
            "TOOL_BACKEND_NOT_CONNECTED",
            "The backend implementation for this tool is not connected yet"));
  }

  public record EmptyInput() {}

  public record UnavailableToolData(String reason) {}

  public record PassengerSummaryInput(
      @NotNull @Min(0) Integer adult,
      @NotNull @Min(0) Integer child,
      @NotNull @Min(0) Integer infant) {}

  public record RevalidateFareInput(
      @NotNull Long flightId,
      @NotNull Long fareId,
      String promoCode,
      @NotNull PassengerSummaryInput passengerSummary) {}

  public record RevalidateFareOutput(
      String revalidateId,
      long expiresInSeconds,
      Long flightId,
      Long fareId,
      String promoCode,
      PassengerSummaryInput passengerSummary,
      RevalidatePricingOutput pricing) {

    private static RevalidateFareOutput from(RevalidateFareView view) {
      return new RevalidateFareOutput(
          view.revalidateId(),
          view.expiresInSeconds(),
          view.flightId(),
          view.fareId(),
          view.promoCode(),
          new PassengerSummaryInput(
              view.passengerSummary().adult(),
              view.passengerSummary().child(),
              view.passengerSummary().infant()),
          RevalidatePricingOutput.from(view.pricing()));
    }
  }

  public record RevalidateItineraryInput(
      @NotBlank String tripType,
      String promoCode,
      @NotNull PassengerSummaryInput passengerSummary,
      @NotNull java.util.List<SegmentInput> segments) {}

  public record SegmentInput(
      @NotBlank String direction, @NotNull Long flightId, @NotNull Long fareId) {}

  public record RevalidateItineraryOutput(
      String revalidateId,
      long expiresInSeconds,
      String tripType,
      String promoCode,
      PassengerSummaryInput passengerSummary,
      RevalidatePricingOutput pricing,
      java.util.List<SegmentOutput> segments) {

    private static RevalidateItineraryOutput from(RevalidateItineraryView view) {
      return new RevalidateItineraryOutput(
          view.revalidateId(),
          view.expiresInSeconds(),
          view.tripType(),
          view.promoCode(),
          new PassengerSummaryInput(
              view.passengerSummary().adult(),
              view.passengerSummary().child(),
              view.passengerSummary().infant()),
          RevalidatePricingOutput.from(view.pricing()),
          view.segments().stream().map(SegmentOutput::from).toList());
    }
  }

  public record SegmentOutput(
      String direction,
      Long flightId,
      Long fareId,
      Long fareClassId,
      String fareCode,
      String fareName,
      String cabinClass,
      String currency,
      RevalidatePricingOutput pricing) {

    private static SegmentOutput from(RevalidateItineraryView.SegmentView view) {
      return new SegmentOutput(
          view.direction(),
          view.flightId(),
          view.fareId(),
          view.fareClassId(),
          view.fareCode(),
          view.fareName(),
          view.cabinClass(),
          view.currency(),
          RevalidatePricingOutput.from(view.pricing()));
    }
  }

  public record RevalidatePricingOutput(
      String currency,
      BigDecimal baseFare,
      BigDecimal taxes,
      BigDecimal fees,
      BigDecimal originalTotalPrice,
      BigDecimal discountAmount,
      Integer discountPercent,
      BigDecimal discountedTotalPrice) {

    private static RevalidatePricingOutput from(RevalidateFareView.PricingView view) {
      return new RevalidatePricingOutput(
          view.currency(),
          view.baseFare(),
          view.taxes(),
          view.fees(),
          view.originalTotalPrice(),
          view.discountAmount(),
          view.discountPercent(),
          view.discountedTotalPrice());
    }

    private static RevalidatePricingOutput from(RevalidateItineraryView.PricingView view) {
      return new RevalidatePricingOutput(
          view.currency(),
          view.baseFare(),
          view.taxes(),
          view.fees(),
          view.originalTotalPrice(),
          view.discountAmount(),
          view.discountPercent(),
          view.discountedTotalPrice());
    }
  }

  public record FareFamiliesInput(String countryCode, String currency) {}

  public record SearchFlightsInput(
      @NotNull FlightSearchTripType tripType,
      @NotBlank String fromAirportCode,
      @NotBlank String toAirportCode,
      @NotNull LocalDate departureDate,
      LocalDate returnDate,
      @Min(0) int adult,
      @Min(0) int child,
      @Min(0) int infant,
      String promoCode,
      String cursor,
      String outboundCursor,
      String inboundCursor,
      @Min(1) @Max(50) int limit,
      Set<CabinClass> cabinClasses,
      BigDecimal maxBudget,
      TimeOfDayBucket departureTimeBucket,
      TimeOfDayBucket arrivalTimeBucket,
      FlightConnectionFilter connectionFilter,
      FlightSortBy sortBy) {}

  public record SearchFlightCalendarInput(
      @NotNull FlightSearchTripType tripType,
      @NotBlank String fromAirportCode,
      @NotBlank String toAirportCode,
      @NotNull LocalDate centerDate,
      @Min(0) int adult,
      @Min(0) int child,
      @Min(0) int infant,
      String promoCode,
      @Min(1) @Max(50) int limit,
      Set<CabinClass> cabinClasses,
      BigDecimal maxBudget,
      TimeOfDayBucket departureTimeBucket,
      TimeOfDayBucket arrivalTimeBucket,
      FlightConnectionFilter connectionFilter,
      FlightSortBy sortBy,
      @Min(0) @Max(30) int daysBefore,
      @Min(0) @Max(30) int daysAfter) {}
}
