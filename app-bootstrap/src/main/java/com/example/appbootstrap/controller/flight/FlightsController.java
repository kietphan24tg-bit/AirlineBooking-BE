package com.example.appbootstrap.controller.flight;

import com.example.appbootstrap.application.booking.BookingRevalidateService;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareRequest;
import com.example.appbootstrap.controller.booking.dto.RevalidateFareResponse;
import com.example.appbootstrap.controller.flight.dto.FlightSearchQueryRequest;
import com.example.appbootstrap.controller.flight.dto.FlightSearchResponse;
import com.example.common.exception.AppException;
import com.example.moduleflight.application.CabinClass;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/flights")
@Tag(name = "Flights", description = "Flight search endpoints")
public class FlightsController {

  private final FlightSearchFacadeService flightSearchFacadeService;
  private final BookingRevalidateService bookingRevalidateService;
  private final UserRecentAirportService userRecentAirportService;

  public FlightsController(
      FlightSearchFacadeService flightSearchFacadeService,
      BookingRevalidateService bookingRevalidateService,
      UserRecentAirportService userRecentAirportService) {
    this.flightSearchFacadeService = flightSearchFacadeService;
    this.bookingRevalidateService = bookingRevalidateService;
    this.userRecentAirportService = userRecentAirportService;
  }

  @GetMapping("/search")
  @Operation(
      summary = "Search flights",
      description = "Supports one-way and round-trip search with optional promo preview.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Search completed", content = @Content),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
  })
  FlightSearchResponse searchFlights(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @Valid @ParameterObject FlightSearchQueryRequest request) {
    validateBusinessRules(request);
    FlightSearchTripType tripType = parseTripType(request.tripType());
    FlightSearchQuery query =
        new FlightSearchQuery(
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
    FlightSearchResponse response =
        FlightSearchResponse.from(flightSearchFacadeService.search(query));
    if (principal != null) {
      userRecentAirportService.recordSearch(principal.getUserId(), request.from(), request.to());
    }
    return response;
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
    @ApiResponse(responseCode = "200", description = "Fare revalidated", content = @Content),
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
