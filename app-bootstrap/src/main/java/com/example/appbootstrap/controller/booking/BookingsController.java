package com.example.appbootstrap.controller.booking;

import com.example.appbootstrap.application.booking.BookingAncillarySelectionService;
import com.example.appbootstrap.application.booking.BookingRevalidateService;
import com.example.appbootstrap.application.booking.BookingSeatAssignmentService;
import com.example.appbootstrap.controller.booking.dto.CreateBookingRequest;
import com.example.appbootstrap.controller.booking.dto.CreateBookingResponse;
import com.example.appbootstrap.controller.booking.dto.PassengerServiceSelectionRequest;
import com.example.appbootstrap.controller.booking.dto.PassengerServiceSelectionResponse;
import com.example.appbootstrap.controller.booking.dto.SeatAssignmentRequest;
import com.example.appbootstrap.controller.booking.dto.SeatAssignmentResponse;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api")
@Tag(name = "Bookings", description = "Booking, passenger, and ticket flows")
public class BookingsController {

  private final BookingRevalidateService bookingRevalidateService;
  private final BookingSeatAssignmentService bookingSeatAssignmentService;
  private final BookingAncillarySelectionService bookingAncillarySelectionService;

  public BookingsController(
      BookingRevalidateService bookingRevalidateService,
      BookingSeatAssignmentService bookingSeatAssignmentService,
      BookingAncillarySelectionService bookingAncillarySelectionService) {
    this.bookingRevalidateService = bookingRevalidateService;
    this.bookingSeatAssignmentService = bookingSeatAssignmentService;
    this.bookingAncillarySelectionService = bookingAncillarySelectionService;
  }

  @PostMapping("/bookings")
  @Operation(
      summary = "Create booking with revalidate session",
      description =
          "Consumes an active revalidateId, validates passenger ages against flightDate, "
              + "checks fare version and seat availability again, then reserves seats "
              + "for adults and children while infants remain without seats.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Booking created",
        content = @Content(schema = @Schema(implementation = CreateBookingResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(responseCode = "409", description = "Business conflict", content = @Content),
    @ApiResponse(
        responseCode = "410",
        description = "Revalidate session expired",
        content = @Content)
  })
  CreateBookingResponse createBooking(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @Valid @RequestBody CreateBookingRequest request) {
    return bookingRevalidateService.createBooking(
        principal != null ? principal.getUserId() : null, request);
  }

  @PutMapping("/bookings/{bookingId}/seat-assignments")
  @Operation(
      summary = "Assign selected seats to booking passengers",
      description =
          "Assigns concrete flight seats to non-infant passengers after a booking fare hold exists.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Seats assigned",
        content = @Content(schema = @Schema(implementation = SeatAssignmentResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Booking, passenger, or seat not found",
        content = @Content),
    @ApiResponse(responseCode = "409", description = "Seat assignment conflict", content = @Content)
  })
  SeatAssignmentResponse assignSeats(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable Long bookingId,
      @Valid @RequestBody SeatAssignmentRequest request) {
    return bookingSeatAssignmentService.assignSeats(
        principal != null ? principal.getUserId() : null, bookingId, request);
  }

  @PutMapping("/bookings/{bookingId}/services")
  @Operation(
      summary = "Assign selected services to booking passengers",
      description =
          "Replaces ancillary service selections for the submitted passengers and refreshes the "
              + "booking total before payment.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Services assigned",
        content =
            @Content(schema = @Schema(implementation = PassengerServiceSelectionResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Booking, passenger, or service option not found",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Service selection conflict",
        content = @Content),
    @ApiResponse(responseCode = "410", description = "Booking expired", content = @Content)
  })
  PassengerServiceSelectionResponse assignServices(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable Long bookingId,
      @Valid @RequestBody PassengerServiceSelectionRequest request) {
    return bookingAncillarySelectionService.updateServices(
        principal != null ? principal.getUserId() : null, bookingId, request);
  }
}
