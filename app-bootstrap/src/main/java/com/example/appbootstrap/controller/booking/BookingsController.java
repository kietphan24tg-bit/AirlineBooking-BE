package com.example.appbootstrap.controller.booking;

import com.example.appbootstrap.application.booking.BookingRevalidateService;
import com.example.appbootstrap.controller.booking.dto.CreateBookingRequest;
import com.example.appbootstrap.controller.booking.dto.CreateBookingResponse;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api")
@Tag(name = "Bookings", description = "Booking, passenger, and ticket flows")
public class BookingsController {

  private final BookingRevalidateService bookingRevalidateService;

  public BookingsController(BookingRevalidateService bookingRevalidateService) {
    this.bookingRevalidateService = bookingRevalidateService;
  }

  @PostMapping("/bookings")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Create booking with revalidate session",
      description =
          "Consumes an active revalidateId, checks fare version and seat availability again, then creates booking.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Booking created", content = @Content),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
    @ApiResponse(responseCode = "409", description = "Business conflict", content = @Content),
    @ApiResponse(
        responseCode = "410",
        description = "Revalidate session expired",
        content = @Content)
  })
  CreateBookingResponse createBooking(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @Valid @RequestBody CreateBookingRequest request) {
    return bookingRevalidateService.createBooking(principal.getUserId(), request);
  }
}
