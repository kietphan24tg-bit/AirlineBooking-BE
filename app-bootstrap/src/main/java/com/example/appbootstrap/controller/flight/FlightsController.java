package com.example.appbootstrap.controller.flight;

import com.example.appbootstrap.application.booking.BookingRevalidateService;
import com.example.appbootstrap.application.flight.FlightSeatMapService;
import com.example.appbootstrap.application.flight.PromotionCatalogService;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareResponse;
import com.example.appbootstrap.controller.booking.dto.RevalidateItineraryRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateItineraryResponse;
import com.example.appbootstrap.controller.flight.dto.FareFamilyCatalogResponse;
import com.example.appbootstrap.controller.flight.dto.FlightCalendarSearchQueryRequest;
import com.example.appbootstrap.controller.flight.dto.FlightCalendarSearchResponse;
import com.example.appbootstrap.controller.flight.dto.FlightSearchQueryRequest;
import com.example.appbootstrap.controller.flight.dto.FlightSearchResponse;
import com.example.appbootstrap.controller.flight.dto.FlightSeatMapResponse;
import com.example.appbootstrap.controller.flight.dto.PromotionCatalogResponse;
import com.example.common.exception.AppException;
import com.example.moduleflight.application.CabinClass;
import com.example.moduleflight.application.FareFamilyCatalogService;
import com.example.moduleflight.application.FlightCalendarSearchQuery;
import com.example.moduleflight.application.FlightCalendarSearchService;
import com.example.moduleflight.application.FlightConnectionFilter;
import com.example.moduleflight.application.FlightSearchFacadeService;
import com.example.moduleflight.application.FlightSearchQuery;
import com.example.moduleflight.application.FlightSearchTripType;
import com.example.moduleflight.application.FlightSortBy;
import com.example.moduleflight.application.TimeOfDayBucket;
import com.example.moduleflight.application.UserRecentAirportService;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/flights")
@Tag(name = "Flights", description = "Flight search endpoints")
public class FlightsController {

  private static final int MAX_CALENDAR_WINDOW_DAYS = 15;

  private final FlightSearchFacadeService flightSearchFacadeService;
  private final FlightCalendarSearchService flightCalendarSearchService;
  private final FareFamilyCatalogService fareFamilyCatalogService;
  private final BookingRevalidateService bookingRevalidateService;
  private final FlightSeatMapService flightSeatMapService;
  private final PromotionCatalogService promotionCatalogService;
  private final UserRecentAirportService userRecentAirportService;

  public FlightsController(
      FlightSearchFacadeService flightSearchFacadeService,
      FlightCalendarSearchService flightCalendarSearchService,
      FareFamilyCatalogService fareFamilyCatalogService,
      BookingRevalidateService bookingRevalidateService,
      FlightSeatMapService flightSeatMapService,
      PromotionCatalogService promotionCatalogService,
      UserRecentAirportService userRecentAirportService) {
    this.flightSearchFacadeService = flightSearchFacadeService;
    this.flightCalendarSearchService = flightCalendarSearchService;
    this.fareFamilyCatalogService = fareFamilyCatalogService;
    this.bookingRevalidateService = bookingRevalidateService;
    this.flightSeatMapService = flightSeatMapService;
    this.promotionCatalogService = promotionCatalogService;
    this.userRecentAirportService = userRecentAirportService;
  }

  @GetMapping("/fare-families")
  @Operation(
      summary = "List fare family catalog",
      description =
          "Returns fare-class policy data independent of flight inventory, with optional "
              + "reference pricing by market.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Fare family catalog returned",
        content = @Content(schema = @Schema(implementation = FareFamilyCatalogResponse.class)))
  })
  FareFamilyCatalogResponse getFareFamilies(
      @RequestParam(value = "countryCode", required = false) String countryCode,
      @RequestParam(value = "currency", required = false) String currency) {
    return FareFamilyCatalogResponse.from(
        fareFamilyCatalogService.listFareFamilies(countryCode, currency));
  }

  @GetMapping("/search")
  @Operation(
      summary = "Search flights",
      description = "Supports one-way and round-trip search with optional promo preview.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Search completed",
        content = @Content(schema = @Schema(implementation = FlightSearchResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
  })
  FlightSearchResponse searchFlights(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @Valid @ParameterObject FlightSearchQueryRequest request) {
    validateBusinessRules(request);
    FlightSearchResponse response =
        FlightSearchResponse.from(flightSearchFacadeService.search(buildSearchQuery(request)));
    if (principal != null) {
      userRecentAirportService.recordSearch(principal.getUserId(), request.from(), request.to());
    }
    return response;
  }

  @GetMapping("/search-calendar")
  @Operation(
      summary = "Search flights across a calendar window",
      description =
          "Returns one-way flight summaries and per-day flight lists for the center date and the surrounding days.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Calendar search completed",
        content = @Content(schema = @Schema(implementation = FlightCalendarSearchResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
  })
  FlightCalendarSearchResponse searchCalendar(
      @Valid @ParameterObject FlightCalendarSearchQueryRequest request) {
    validateCalendarBusinessRules(request);
    return FlightCalendarSearchResponse.from(
        flightCalendarSearchService.search(buildCalendarSearchQuery(request)));
  }

  @PostMapping("/revalidate-fare")
  @Operation(
      summary = "Revalidate selected fare",
      description =
          "Checks fare/availability/promotion, computes authoritative price using "
              + "ADULT/CHILD/INFANT rules, verifies seats for adult+child only, "
              + "and returns revalidateId without reserving seats.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Fare revalidated",
        content = @Content(schema = @Schema(implementation = RevalidateFareResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Flight or fare not found",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Fare sold out or price changed",
        content = @Content)
  })
  RevalidateFareResponse revalidateFare(@Valid @RequestBody RevalidateFareRequest request) {
    return bookingRevalidateService.revalidate(request);
  }

  @PostMapping("/revalidate-itinerary")
  @Operation(
      summary = "Revalidate itinerary fares",
      description =
          "Validates one-way or round-trip fare selections, computes aggregate pricing, verifies "
              + "seat availability for all selected segments, and returns a revalidateId without "
              + "reserving seats.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Itinerary revalidated",
        content = @Content(schema = @Schema(implementation = RevalidateItineraryResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Flight or fare not found",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Fare sold out or price changed",
        content = @Content)
  })
  RevalidateItineraryResponse revalidateItinerary(
      @Valid @RequestBody RevalidateItineraryRequest request) {
    return bookingRevalidateService.revalidateItinerary(request);
  }

  @GetMapping("/promotions")
  @Operation(
      summary = "List active promotion codes",
      description =
          "Returns currently active promotion codes from seeded or persisted promotion data so the "
              + "frontend can display suggested promo chips.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Promotion list returned",
        content = @Content(schema = @Schema(implementation = PromotionCatalogResponse.class)))
  })
  PromotionCatalogResponse getPromotions() {
    return PromotionCatalogResponse.from(promotionCatalogService.getActivePromotions());
  }

  @GetMapping("/{flightId}/seats")
  @Operation(
      summary = "Get seat map for a selected fare",
      description =
          "Returns flight seats with fare-aware entitlement so the UI can distinguish included, "
              + "paid, and unavailable seats.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Seat map returned",
        content = @Content(schema = @Schema(implementation = FlightSeatMapResponse.class))),
    @ApiResponse(responseCode = "404", description = "Flight or fare not found", content = @Content)
  })
  FlightSeatMapResponse getSeatMap(
      @PathVariable Long flightId, @RequestParam("fareId") Long fareId) {
    return FlightSeatMapResponse.from(flightSeatMapService.getSeatMap(flightId, fareId));
  }

  private void validateBusinessRules(FlightSearchQueryRequest request) {
    FlightSearchTripType tripType = parseTripType(request.tripType());
    if (tripType == FlightSearchTripType.ROUND_TRIP) {
      if (request.returnDate() == null) {
        throw new AppException(
            "RETURN_DATE_REQUIRED", "returnDate is required for round-trip", 400);
      }
      LocalDate departureDate = request.departureDate();
      if (departureDate != null && request.returnDate().isBefore(departureDate)) {
        throw new AppException(
            "INVALID_RETURN_DATE", "returnDate must be on or after departureDate", 400);
      }
    }
    if (request.effectiveInfant() > request.adult()) {
      throw new AppException(
          "INVALID_PASSENGER_BREAKDOWN", "infant count must not exceed adult count", 400);
    }
    if (request.totalPassengers() > 9) {
      throw new AppException(
          "INVALID_PASSENGER_BREAKDOWN", "total passengers must not exceed 9", 400);
    }
  }

  private void validateCalendarBusinessRules(FlightCalendarSearchQueryRequest request) {
    FlightSearchTripType tripType = parseTripType(request.tripType());
    if (tripType != FlightSearchTripType.ONE_WAY) {
      throw new AppException(
          "INVALID_TRIP_TYPE", "search-calendar currently supports ONE_WAY only", 400);
    }
    if (request.effectiveInfant() > request.adult()) {
      throw new AppException(
          "INVALID_PASSENGER_BREAKDOWN", "infant count must not exceed adult count", 400);
    }
    if (request.totalPassengers() > 9) {
      throw new AppException(
          "INVALID_PASSENGER_BREAKDOWN", "total passengers must not exceed 9", 400);
    }
    int totalWindowDays = request.effectiveDaysBefore() + request.effectiveDaysAfter() + 1;
    if (totalWindowDays > MAX_CALENDAR_WINDOW_DAYS) {
      throw new AppException(
          "INVALID_CALENDAR_WINDOW",
          "daysBefore + daysAfter + 1 must not exceed " + MAX_CALENDAR_WINDOW_DAYS,
          400);
    }
  }

  private FlightSearchQuery buildSearchQuery(FlightSearchQueryRequest request) {
    FlightSearchTripType tripType = parseTripType(request.tripType());
    return new FlightSearchQuery(
        tripType,
        request.from(),
        request.to(),
        request.departureDate(),
        request.returnDate(),
        request.adult(),
        request.effectiveChild(),
        request.effectiveInfant(),
        request.promoCode(),
        request.cursor(),
        request.outboundCursor(),
        request.inboundCursor(),
        request.effectiveLimit(),
        parseCabinClasses(request.cabinClasses()),
        request.maxBudget(),
        parseTimeBucket(request.departureTimeBucket()),
        parseTimeBucket(request.arrivalTimeBucket()),
        parseConnectionFilter(request.connectionFilter()),
        parseSortBy(request.sortBy()));
  }

  private FlightCalendarSearchQuery buildCalendarSearchQuery(
      FlightCalendarSearchQueryRequest request) {
    return new FlightCalendarSearchQuery(
        parseTripType(request.tripType()),
        request.from(),
        request.to(),
        request.centerDate(),
        request.adult(),
        request.effectiveChild(),
        request.effectiveInfant(),
        request.promoCode(),
        request.effectiveLimit(),
        parseCabinClasses(request.cabinClasses()),
        request.maxBudget(),
        parseTimeBucket(request.departureTimeBucket()),
        parseTimeBucket(request.arrivalTimeBucket()),
        parseConnectionFilter(request.connectionFilter()),
        parseSortBy(request.sortBy()),
        request.effectiveDaysBefore(),
        request.effectiveDaysAfter());
  }

  private FlightSearchTripType parseTripType(String tripType) {
    if (tripType == null || tripType.isBlank()) {
      throw new AppException("INVALID_TRIP_TYPE", "tripType is required", 400);
    }
    try {
      return FlightSearchTripType.valueOf(tripType.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new AppException("INVALID_TRIP_TYPE", "tripType must be ONE_WAY or ROUND_TRIP", 400);
    }
  }

  private Set<CabinClass> parseCabinClasses(List<String> cabinClasses) {
    if (cabinClasses == null || cabinClasses.isEmpty()) {
      return Collections.emptySet();
    }
    Set<CabinClass> out = HashSet.newHashSet(cabinClasses.size());
    for (String raw : cabinClasses) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      try {
        out.add(CabinClass.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ex) {
        throw new AppException(
            "INVALID_CABIN_CLASS",
            "cabinClasses must be ECONOMY, PREMIUM_ECONOMY, BUSINESS, or FIRST",
            400);
      }
    }
    return Set.copyOf(out);
  }

  /**
   * @return {@code null} when the client omits the param, sends blank, or ANY — meaning no time
   *     filter.
   */
  private TimeOfDayBucket parseTimeBucket(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String upper = raw.trim().toUpperCase(Locale.ROOT);
    if ("ANY".equals(upper)) {
      return null;
    }
    try {
      TimeOfDayBucket bucket = TimeOfDayBucket.valueOf(upper);
      if (bucket == TimeOfDayBucket.ANY) {
        return null;
      }
      return bucket;
    } catch (IllegalArgumentException ex) {
      throw new AppException(
          "INVALID_TIME_BUCKET",
          "departureTimeBucket and arrivalTimeBucket must be MORNING, AFTERNOON, EVENING, or ANY",
          400);
    }
  }

  private FlightConnectionFilter parseConnectionFilter(String raw) {
    if (raw == null || raw.isBlank()) {
      return FlightConnectionFilter.ANY;
    }
    try {
      return FlightConnectionFilter.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new AppException(
          "INVALID_CONNECTION_FILTER", "connectionFilter must be ANY or DIRECT_ONLY", 400);
    }
  }

  private FlightSortBy parseSortBy(String raw) {
    if (raw == null || raw.isBlank()) {
      return FlightSortBy.RECOMMENDED;
    }
    try {
      return FlightSortBy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new AppException(
          "INVALID_SORT_BY",
          "sortBy must be RECOMMENDED, CHEAPEST, FASTEST, or BEST_EXPERIENCE",
          400);
    }
  }
}
