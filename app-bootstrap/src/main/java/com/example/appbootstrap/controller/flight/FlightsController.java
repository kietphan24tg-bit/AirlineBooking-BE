package com.example.appbootstrap.controller.flight;

import com.example.appbootstrap.controller.flight.dto.FlightSearchQueryRequest;
import com.example.appbootstrap.controller.flight.dto.FlightSearchResponse;
import com.example.common.exception.AppException;
import com.example.moduleflight.application.FlightSearchFacadeService;
import com.example.moduleflight.application.FlightSearchQuery;
import com.example.moduleflight.application.FlightSearchTripType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/flights")
@Tag(name = "Flights", description = "Flight search endpoints")
public class FlightsController {

  private final FlightSearchFacadeService flightSearchFacadeService;

  public FlightsController(FlightSearchFacadeService flightSearchFacadeService) {
    this.flightSearchFacadeService = flightSearchFacadeService;
  }

  @GetMapping("/search")
  @Operation(
      summary = "Search flights",
      description = "Supports one-way and round-trip search with optional promo preview.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Search completed", content = @Content),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
  })
  FlightSearchResponse searchFlights(@Valid @ModelAttribute FlightSearchQueryRequest request) {
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
            request.effectiveLimit());
    return FlightSearchResponse.from(flightSearchFacadeService.search(query));
  }

  private void validateBusinessRules(FlightSearchQueryRequest request) {
    if (request.from() != null
        && request.to() != null
        && request.from().trim().equalsIgnoreCase(request.to().trim())) {
      throw new AppException("INVALID_ROUTE", "from and to must be different airports", 400);
    }
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
}
