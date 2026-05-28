package com.example.appbootstrap.controller.booking;

import com.example.appbootstrap.application.booking.BookingAncillarySelectionService;
import com.example.appbootstrap.application.booking.BookingLookupService;
import com.example.appbootstrap.application.booking.BookingManageOptionsService;
import com.example.appbootstrap.application.booking.BookingRevalidateService;
import com.example.appbootstrap.application.booking.BookingSeatAssignmentService;
import com.example.appbootstrap.application.booking.FlightChangeConfirmService;
import com.example.appbootstrap.application.booking.FlightChangeQuoteService;
import com.example.appbootstrap.controller.booking.dto.BookingCancellationPreviewRequest;
import com.example.appbootstrap.controller.booking.dto.BookingCancellationPreviewResponse;
import com.example.appbootstrap.controller.booking.dto.BookingCancellationRequest;
import com.example.appbootstrap.controller.booking.dto.BookingCancellationResponse;
import com.example.appbootstrap.controller.booking.dto.BookingLookupRequest;
import com.example.appbootstrap.controller.booking.dto.BookingLookupResponse;
import com.example.appbootstrap.controller.booking.dto.BookingManageOptionsResponse;
import com.example.appbootstrap.controller.booking.dto.BookingTicketsResponse;
import com.example.appbootstrap.controller.booking.dto.CreateBookingRequest;
import com.example.appbootstrap.controller.booking.dto.CreateBookingResponse;
import com.example.appbootstrap.controller.booking.dto.FlightChangeConfirmRequest;
import com.example.appbootstrap.controller.booking.dto.FlightChangeConfirmResponse;
import com.example.appbootstrap.controller.booking.dto.FlightChangeQuoteRequest;
import com.example.appbootstrap.controller.booking.dto.FlightChangeQuoteResponse;
import com.example.appbootstrap.controller.booking.dto.PassengerServiceSelectionRequest;
import com.example.appbootstrap.controller.booking.dto.PassengerServiceSelectionResponse;
import com.example.appbootstrap.controller.booking.dto.SeatAssignmentRequest;
import com.example.appbootstrap.controller.booking.dto.SeatAssignmentResponse;
import com.example.appbootstrap.controller.booking.dto.SeatChangeConfirmRequest;
import com.example.appbootstrap.controller.booking.dto.SeatChangeConfirmResponse;
import com.example.appbootstrap.controller.booking.dto.SeatChangePreviewRequest;
import com.example.appbootstrap.controller.booking.dto.SeatChangePreviewResponse;
import com.example.appbootstrap.controller.booking.dto.SeatChangeRequest;
import com.example.appbootstrap.controller.booking.dto.ServiceChangeConfirmRequest;
import com.example.appbootstrap.controller.booking.dto.ServiceChangeConfirmResponse;
import com.example.appbootstrap.controller.booking.dto.ServiceChangePreviewRequest;
import com.example.appbootstrap.controller.booking.dto.ServiceChangePreviewResponse;
import com.example.modulebooking.application.BookingTicketsView;
import com.example.modulebooking.application.TicketIssuanceService;
import com.example.moduleidentity.application.security.AppUserPrincipal;
import com.example.modulepayment.application.BookingCancellationPreviewView;
import com.example.modulepayment.application.BookingCancellationResultView;
import com.example.modulepayment.application.BookingCancellationService;
import com.example.modulepayment.application.SeatChangeConfirmView;
import com.example.modulepayment.application.SeatChangePreviewView;
import com.example.modulepayment.application.SeatChangeSettlementService;
import com.example.modulepayment.application.ServiceChangeConfirmView;
import com.example.modulepayment.application.ServiceChangePreviewView;
import com.example.modulepayment.application.ServiceChangeSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
  private final BookingLookupService bookingLookupService;
  private final BookingManageOptionsService bookingManageOptionsService;
  private final BookingSeatAssignmentService bookingSeatAssignmentService;
  private final BookingAncillarySelectionService bookingAncillarySelectionService;
  private final FlightChangeQuoteService flightChangeQuoteService;
  private final FlightChangeConfirmService flightChangeConfirmService;
  private final TicketIssuanceService ticketIssuanceService;
  private final BookingCancellationService bookingCancellationService;
  private final SeatChangeSettlementService seatChangeSettlementService;
  private final ServiceChangeSettlementService serviceChangeSettlementService;

  public BookingsController(
      BookingRevalidateService bookingRevalidateService,
      BookingLookupService bookingLookupService,
      BookingManageOptionsService bookingManageOptionsService,
      BookingSeatAssignmentService bookingSeatAssignmentService,
      BookingAncillarySelectionService bookingAncillarySelectionService,
      FlightChangeQuoteService flightChangeQuoteService,
      FlightChangeConfirmService flightChangeConfirmService,
      TicketIssuanceService ticketIssuanceService,
      BookingCancellationService bookingCancellationService,
      SeatChangeSettlementService seatChangeSettlementService,
      ServiceChangeSettlementService serviceChangeSettlementService) {
    this.bookingRevalidateService = bookingRevalidateService;
    this.bookingLookupService = bookingLookupService;
    this.bookingManageOptionsService = bookingManageOptionsService;
    this.bookingSeatAssignmentService = bookingSeatAssignmentService;
    this.bookingAncillarySelectionService = bookingAncillarySelectionService;
    this.flightChangeQuoteService = flightChangeQuoteService;
    this.flightChangeConfirmService = flightChangeConfirmService;
    this.ticketIssuanceService = ticketIssuanceService;
    this.bookingCancellationService = bookingCancellationService;
    this.seatChangeSettlementService = seatChangeSettlementService;
    this.serviceChangeSettlementService = serviceChangeSettlementService;
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

  @PostMapping("/bookings/lookup")
  @Operation(
      summary = "Lookup booking by PNR and passenger last name",
      description =
          "Validates the submitted PNR and last name pair, then returns booking summary data "
              + "for the manage-booking overview page.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Booking summary returned",
        content = @Content(schema = @Schema(implementation = BookingLookupResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content)
  })
  BookingLookupResponse lookupBooking(@Valid @RequestBody BookingLookupRequest request) {
    return bookingLookupService.lookup(request);
  }

  @GetMapping("/bookings/{bookingId}/manage-options")
  @Operation(
      summary = "Get booking-scoped seat and service selections",
      description =
          "Returns passengers, itinerary fare context, current seat assignments, and current "
              + "ancillary selections for the manage-booking seat and service flows.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Manage seat and service context returned",
        content = @Content(schema = @Schema(implementation = BookingManageOptionsResponse.class))),
    @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content)
  })
  BookingManageOptionsResponse getManageOptions(
      @AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long bookingId) {
    return BookingManageOptionsResponse.from(
        bookingManageOptionsService.getManageOptions(
            principal != null ? principal.getUserId() : null, bookingId));
  }

  @PostMapping("/bookings/{bookingId}/flight-change/quote")
  @Operation(
      summary = "Preview flight change eligibility and settlement",
      description =
          "Validates the selected tickets against fare rules, booking state, live inventory, "
              + "and reprices the proposed replacement flight without committing the change.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Flight change quote returned",
        content = @Content(schema = @Schema(implementation = FlightChangeQuoteResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Booking, ticket, or flight not found",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Flight change is not allowed",
        content = @Content)
  })
  FlightChangeQuoteResponse quoteFlightChange(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable Long bookingId,
      @Valid @RequestBody FlightChangeQuoteRequest request) {
    return flightChangeQuoteService.quote(
        principal == null ? null : principal.getUserId(), bookingId, request);
  }

  @PostMapping("/bookings/{bookingId}/flight-changes/confirm")
  @Operation(
      summary = "Confirm flight change settlement",
      description =
          "Revalidates the quoted flight change and returns whether the booking completes, "
              + "triggers a refund, or requires additional payment.")
  FlightChangeConfirmResponse confirmFlightChange(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable Long bookingId,
      @Valid @RequestBody FlightChangeConfirmRequest request) {
    return flightChangeConfirmService.confirm(
        principal == null ? null : principal.getUserId(), bookingId, request);
  }

  @GetMapping("/bookings/{bookingId}/tickets")
  @Operation(
      summary = "Issue and get tickets for a confirmed booking",
      description =
          "Issues missing tickets idempotently, then returns the booking PNR and ticket details "
              + "after payment webhook confirmation.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Issued tickets returned",
        content = @Content(schema = @Schema(implementation = BookingTicketsResponse.class))),
    @ApiResponse(responseCode = "404", description = "Booking not found", content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Booking is not confirmed or tickets are not issued yet",
        content = @Content)
  })
  BookingTicketsResponse getTickets(
      @AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long bookingId) {
    return toBookingTicketsResponse(
        ticketIssuanceService.issueTicketsForBooking(
            principal != null ? principal.getUserId() : null, bookingId));
  }

  @PutMapping("/bookings/{bookingId}/seat-assignments")
  @Operation(
      summary = "Assign selected seats to booking passengers",
      description =
          "Assigns concrete flight seats to non-infant passengers after a booking fare hold"
              + " exists.")
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

  @DeleteMapping("/bookings/{bookingId}/seat-assignments")
  @Operation(
      operationId = "unassignBookingSeats",
      summary = "Release selected seat assignments for booking passengers",
      description =
          "Cancels concrete flight seat links for the listed passenger and seat pairs; "
              + "applies when the booking is eligible for seat changes (same rules as assign).")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      description = "Passenger-seat pairs to release from the booking",
      content = @Content(schema = @Schema(implementation = SeatAssignmentRequest.class)))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Seats unassigned, remaining active assignments and totals returned",
        content = @Content(schema = @Schema(implementation = SeatAssignmentResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Booking, passenger, seat, or active assignment not found",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Could not release seat on flight or business conflict",
        content = @Content)
  })
  SeatAssignmentResponse unassignSeats(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable Long bookingId,
      @Valid @RequestBody SeatAssignmentRequest request) {
    return bookingSeatAssignmentService.unassignSeats(
        principal != null ? principal.getUserId() : null, bookingId, request);
  }

  @PatchMapping("/bookings/{bookingId}/seat-assignment")
  @Operation(
      summary = "Change or cancel a passenger seat assignment",
      description =
          "Releases the current passenger seat and optionally assigns a new one in the same "
              + "transaction. Omit toSeatId to cancel the current seat only.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Seat change processed",
        content = @Content(schema = @Schema(implementation = SeatAssignmentResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Booking, passenger, seat, or source assignment not found",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Seat change conflict or business rule violation",
        content = @Content)
  })
  SeatAssignmentResponse changeSeat(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable Long bookingId,
      @Valid @RequestBody SeatChangeRequest request) {
    return bookingSeatAssignmentService.changeSeat(
        principal != null ? principal.getUserId() : null, bookingId, request);
  }

  @PostMapping("/bookings/{bookingId}/seat-changes/preview")
  @Operation(
      summary = "Preview seat change settlement",
      description =
          "Validates current seat changes against live booking assignments and recalculates the "
              + "authoritative additional payment or refund.")
  SeatChangePreviewResponse previewSeatChanges(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable Long bookingId,
      @Valid @RequestBody SeatChangePreviewRequest request) {
    SeatChangePreviewView view =
        seatChangeSettlementService.preview(
            principal == null ? null : principal.getUserId(),
            bookingId,
            request.changes().stream()
                .map(
                    change ->
                        new SeatChangeSettlementService.ChangeCommand(
                            change.passengerId(),
                            change.flightId(),
                            change.fromSeatId(),
                            change.toSeatId()))
                .toList());
    return toSeatChangePreviewResponse(view);
  }

  @PostMapping("/bookings/{bookingId}/seat-changes/confirm")
  @Operation(
      summary = "Confirm seat change settlement",
      description =
          "Revalidates seat-change totals and either completes the settlement, starts a refund, "
              + "or marks the booking as requiring additional payment.")
  SeatChangeConfirmResponse confirmSeatChanges(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable Long bookingId,
      @Valid @RequestBody SeatChangeConfirmRequest request) {
    SeatChangeConfirmView view =
        seatChangeSettlementService.confirm(
            principal == null ? null : principal.getUserId(),
            bookingId,
            request.changes().stream()
                .map(
                    change ->
                        new SeatChangeSettlementService.ChangeCommand(
                            change.passengerId(),
                            change.flightId(),
                            change.fromSeatId(),
                            change.toSeatId()))
                .toList(),
            request.idempotencyKey(),
            request.expectedAdditionalAmount(),
            request.expectedRefundAmount(),
            request.expectedNetAmount());
    return toSeatChangeConfirmResponse(view);
  }

  @PostMapping("/bookings/{bookingId}/service-changes/preview")
  @Operation(
      summary = "Preview ancillary service change settlement",
      description =
          "Validates current ancillary service changes and recalculates the authoritative "
              + "additional payment or refund before confirmation.")
  ServiceChangePreviewResponse previewServiceChanges(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable Long bookingId,
      @Valid @RequestBody ServiceChangePreviewRequest request) {
    ServiceChangePreviewView view =
        serviceChangeSettlementService.preview(
            principal == null ? null : principal.getUserId(),
            bookingId,
            request.passengers().stream()
                .map(
                    passenger ->
                        new ServiceChangeSettlementService.PassengerSelectionsCommand(
                            passenger.passengerId(),
                            passenger.selections().stream()
                                .map(
                                    selection ->
                                        new ServiceChangeSettlementService.SelectionCommand(
                                            selection.optionId(), selection.quantity()))
                                .toList()))
                .toList());
    return toServiceChangePreviewResponse(view);
  }

  @PostMapping("/bookings/{bookingId}/service-changes/confirm")
  @Operation(
      summary = "Confirm ancillary service change settlement",
      description =
          "Revalidates ancillary-service totals and either completes the change, starts a refund, "
              + "or marks the booking as requiring additional payment.")
  ServiceChangeConfirmResponse confirmServiceChanges(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable Long bookingId,
      @Valid @RequestBody ServiceChangeConfirmRequest request) {
    ServiceChangeConfirmView view =
        serviceChangeSettlementService.confirm(
            principal == null ? null : principal.getUserId(),
            bookingId,
            request.passengers().stream()
                .map(
                    passenger ->
                        new ServiceChangeSettlementService.PassengerSelectionsCommand(
                            passenger.passengerId(),
                            passenger.selections().stream()
                                .map(
                                    selection ->
                                        new ServiceChangeSettlementService.SelectionCommand(
                                            selection.optionId(), selection.quantity()))
                                .toList()))
                .toList(),
            request.previewToken(),
            request.idempotencyKey(),
            request.expectedAdditionalAmount(),
            request.expectedRefundAmount(),
            request.expectedNetAmount());
    return toServiceChangeConfirmResponse(view);
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

  @PostMapping("/bookings/{bookingId}/cancellations/preview")
  @Operation(
      summary = "Preview booking ticket cancellation",
      description =
          "Returns passenger-level refund totals before confirming cancellation for the selected"
              + " passengers.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Cancellation preview returned",
        content =
            @Content(schema = @Schema(implementation = BookingCancellationPreviewResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Booking or ticket not found",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Booking or ticket is not eligible for cancellation",
        content = @Content)
  })
  BookingCancellationPreviewResponse previewCancellation(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable Long bookingId,
      @Valid @RequestBody BookingCancellationPreviewRequest request) {
    BookingCancellationPreviewView view =
        bookingCancellationService.preview(
            principal == null ? null : principal.getUserId(), bookingId, request.ticketIds());
    return toPreviewResponse(view);
  }

  @PostMapping("/bookings/{bookingId}/cancellations")
  @Operation(
      summary = "Confirm booking ticket cancellation",
      description =
          "Refunds the selected passengers for a confirmed booking and updates tickets, seats, and"
              + " services.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Cancellation processed",
        content = @Content(schema = @Schema(implementation = BookingCancellationResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Booking or ticket not found",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Cancellation cannot be completed",
        content = @Content)
  })
  BookingCancellationResponse cancelBooking(
      @AuthenticationPrincipal AppUserPrincipal principal,
      @PathVariable Long bookingId,
      @Valid @RequestBody BookingCancellationRequest request) {
    BookingCancellationResultView view =
        bookingCancellationService.cancel(
            principal == null ? null : principal.getUserId(),
            bookingId,
            request.ticketIds(),
            request.reason(),
            request.idempotencyKey(),
            request.expectedNetRefund(),
            request.expectedRefundFee());
    return toCancellationResponse(view);
  }

  private BookingTicketsResponse toBookingTicketsResponse(BookingTicketsView view) {
    return new BookingTicketsResponse(
        view.bookingId(),
        view.pnrCode(),
        view.tickets().stream().map(this::ticketResponse).toList());
  }

  private SeatChangePreviewResponse toSeatChangePreviewResponse(SeatChangePreviewView view) {
    return new SeatChangePreviewResponse(
        view.bookingId(),
        view.currency(),
        view.previewToken(),
        view.settlementType(),
        view.items().stream()
            .map(
                item ->
                    new SeatChangePreviewResponse.ItemResponse(
                        item.passengerId(),
                        item.passengerName(),
                        item.flightId(),
                        item.flightNumber(),
                        item.departureAirportCode(),
                        item.arrivalAirportCode(),
                        item.departureTime(),
                        item.fromSeatId(),
                        item.fromSeatNumber(),
                        item.toSeatId(),
                        item.toSeatNumber(),
                        item.oldAmount(),
                        item.newAmount(),
                        item.delta(),
                        item.status(),
                        item.message()))
            .toList(),
        new SeatChangePreviewResponse.SummaryResponse(
            view.summary().additionalAmount(),
            view.summary().refundAmount(),
            view.summary().netAmount()));
  }

  private SeatChangeConfirmResponse toSeatChangeConfirmResponse(SeatChangeConfirmView view) {
    SeatChangeConfirmView.RefundMethodView refundMethod = view.refundMethod();
    return new SeatChangeConfirmResponse(
        view.settlementId(),
        view.bookingId(),
        view.currency(),
        view.settlementType(),
        view.status(),
        view.refundStatus(),
        view.provider(),
        view.providerRefundId(),
        view.errorCode(),
        view.errorMessage(),
        refundMethod == null
            ? null
            : new SeatChangeConfirmResponse.RefundMethodResponse(
                refundMethod.type(),
                refundMethod.label(),
                refundMethod.maskedAccount(),
                refundMethod.estimatedDays()),
        view.paymentMethods().stream()
            .map(
                item ->
                    new SeatChangeConfirmResponse.PaymentMethodItemResponse(
                        item.code(), item.label(), item.provider(), item.icon(), item.supported()))
            .toList(),
        new SeatChangeConfirmResponse.SummaryResponse(
            view.summary().additionalAmount(),
            view.summary().refundAmount(),
            view.summary().netAmount()));
  }

  private ServiceChangePreviewResponse toServiceChangePreviewResponse(
      ServiceChangePreviewView view) {
    return new ServiceChangePreviewResponse(
        view.bookingId(),
        view.currency(),
        view.previewToken(),
        view.settlementType(),
        view.items().stream()
            .map(
                item ->
                    new ServiceChangePreviewResponse.ItemResponse(
                        item.passengerId(),
                        item.passengerName(),
                        item.serviceId(),
                        item.serviceType(),
                        item.serviceName(),
                        item.optionId(),
                        item.optionName(),
                        item.quantity(),
                        item.oldAmount(),
                        item.newAmount(),
                        item.delta(),
                        item.status(),
                        item.message()))
            .toList(),
        new ServiceChangePreviewResponse.SummaryResponse(
            view.summary().additionalAmount(),
            view.summary().refundAmount(),
            view.summary().netAmount()));
  }

  private ServiceChangeConfirmResponse toServiceChangeConfirmResponse(
      ServiceChangeConfirmView view) {
    ServiceChangeConfirmView.RefundMethodView refundMethod = view.refundMethod();
    return new ServiceChangeConfirmResponse(
        view.settlementId(),
        view.bookingId(),
        view.currency(),
        view.settlementType(),
        view.status(),
        view.refundStatus(),
        view.provider(),
        view.providerRefundId(),
        view.errorCode(),
        view.errorMessage(),
        refundMethod == null
            ? null
            : new ServiceChangeConfirmResponse.RefundMethodResponse(
                refundMethod.type(),
                refundMethod.label(),
                refundMethod.maskedAccount(),
                refundMethod.estimatedDays()),
        view.paymentMethods().stream()
            .map(
                item ->
                    new ServiceChangeConfirmResponse.PaymentMethodItemResponse(
                        item.code(), item.label(), item.provider(), item.icon(), item.supported()))
            .toList(),
        new ServiceChangeConfirmResponse.SummaryResponse(
            view.summary().additionalAmount(),
            view.summary().refundAmount(),
            view.summary().netAmount()));
  }

  private BookingTicketsResponse.TicketResponse ticketResponse(
      BookingTicketsView.TicketView ticket) {
    return new BookingTicketsResponse.TicketResponse(
        ticket.id(),
        ticket.ticketNumber(),
        ticket.status(),
        ticket.issuedAt(),
        new BookingTicketsResponse.PassengerResponse(
            ticket.passenger().id(),
            ticket.passenger().firstName(),
            ticket.passenger().lastName(),
            ticket.passenger().passengerType()),
        ticket.seat() == null
            ? null
            : new BookingTicketsResponse.SeatResponse(
                ticket.seat().id(), ticket.seat().seatNumber()));
  }

  private BookingCancellationPreviewResponse toPreviewResponse(
      BookingCancellationPreviewView view) {
    return new BookingCancellationPreviewResponse(
        view.bookingId(),
        view.bookingStatus(),
        view.paymentStatus(),
        view.currency(),
        view.items().stream()
            .map(
                item ->
                    new BookingCancellationPreviewResponse.ItemResponse(
                        item.ticketId(),
                        item.passengerId(),
                        item.passengerType(),
                        item.passengerName(),
                        item.eligible(),
                        item.reason(),
                        item.fareGross(),
                        item.promoAllocated(),
                        item.fareNet(),
                        item.seatRefund(),
                        item.serviceRefund(),
                        item.refundFee(),
                        item.netRefund()))
            .toList(),
        new BookingCancellationPreviewResponse.SummaryResponse(
            view.summary().fareGross(),
            view.summary().promoAllocated(),
            view.summary().fareNet(),
            view.summary().seatRefund(),
            view.summary().serviceRefund(),
            view.summary().refundFee(),
            view.summary().netRefund()));
  }

  private BookingCancellationResponse toCancellationResponse(BookingCancellationResultView view) {
    BookingCancellationResultView.RefundMethodView refundMethod = view.refundMethod();
    BookingCancellationResponse.RefundMethodResponse refundMethodResponse =
        refundMethod == null
            ? null
            : new BookingCancellationResponse.RefundMethodResponse(
                refundMethod.type(),
                refundMethod.label(),
                refundMethod.maskedAccount(),
                refundMethod.estimatedDays());
    return new BookingCancellationResponse(
        view.bookingId(),
        view.pnrCode(),
        view.contactEmail(),
        view.cancellationId(),
        view.cancellationCode(),
        view.cancelledAt(),
        view.refundStatus(),
        view.provider(),
        view.providerRefundId(),
        view.bookingStatus(),
        view.paymentStatus(),
        view.currency(),
        view.errorCode(),
        view.errorMessage(),
        view.refundEstimateTitle(),
        view.refundEstimateMessage(),
        view.refundEstimateNote(),
        refundMethodResponse,
        view.items().stream()
            .map(
                item ->
                    new BookingCancellationResponse.ItemResponse(
                        item.ticketId(),
                        item.passengerId(),
                        item.passengerType(),
                        item.passengerName(),
                        item.ticketStatus(),
                        item.fareGross(),
                        item.promoAllocated(),
                        item.fareNet(),
                        item.seatRefund(),
                        item.serviceRefund(),
                        item.refundFee(),
                        item.netRefund()))
            .toList(),
        new BookingCancellationResponse.SummaryResponse(
            view.summary().fareGross(),
            view.summary().promoAllocated(),
            view.summary().fareNet(),
            view.summary().seatRefund(),
            view.summary().serviceRefund(),
            view.summary().refundFee(),
            view.summary().netRefund()));
  }
}
