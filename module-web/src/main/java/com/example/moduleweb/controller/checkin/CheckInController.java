package com.example.moduleweb.controller.checkin;

import com.example.moduleweb.api.ApiSuccessMessage;
import com.example.moduleweb.application.checkin.CheckInSuccessEmailService;
import com.example.moduleweb.controller.checkin.dto.CheckInConfirmRequest;
import com.example.moduleweb.controller.checkin.dto.CheckInConfirmResponse;
import com.example.moduleweb.controller.checkin.dto.CheckInContinueRequest;
import com.example.moduleweb.controller.checkin.dto.CheckInContinueResponse;
import com.example.modulecheckin.application.CheckInConfirmCommand;
import com.example.modulecheckin.application.CheckInConfirmService;
import com.example.modulecheckin.application.CheckInConfirmView;
import com.example.modulecheckin.application.CheckInContinueCommand;
import com.example.modulecheckin.application.CheckInContinueService;
import com.example.modulecheckin.application.CheckInContinueView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/check-in")
@Tag(name = "Check-in", description = "Check-in and boarding pass endpoints")
public class CheckInController {

  private final CheckInConfirmService checkInConfirmService;
  private final CheckInContinueService checkInContinueService;
  private final CheckInSuccessEmailService checkInSuccessEmailService;

  public CheckInController(
      CheckInContinueService checkInContinueService,
      CheckInConfirmService checkInConfirmService,
      CheckInSuccessEmailService checkInSuccessEmailService) {
    this.checkInContinueService = checkInContinueService;
    this.checkInConfirmService = checkInConfirmService;
    this.checkInSuccessEmailService = checkInSuccessEmailService;
  }

  @PostMapping("/continue")
  @ApiSuccessMessage("Check-in confirmation snapshot returned")
  @Operation(
      summary = "Validate selected flights and passengers for online check-in",
      description =
          "Returns a confirmation snapshot for the selected booking segments and passengers without confirming check-in.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Check-in confirmation snapshot returned",
        content = @Content(schema = @Schema(implementation = CheckInContinueResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Booking or selection not found",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Booking not eligible for check-in",
        content = @Content)
  })
  CheckInContinueResponse continueCheckIn(@Valid @RequestBody CheckInContinueRequest request) {
    CheckInContinueView view =
        checkInContinueService.continueCheckIn(
            new CheckInContinueCommand(
                request.pnrCode(),
                request.lastName(),
                request.segments().stream()
                    .map(
                        segment ->
                            new CheckInContinueCommand.SegmentSelection(
                                segment.flightId(), segment.passengerIds()))
                    .toList()));
    return toResponse(view);
  }

  @PostMapping("/confirm")
  @ApiSuccessMessage("Check-in confirmed")
  @Operation(
      summary = "Confirm online check-in for selected flights and passengers",
      description =
          "Performs the final check-in confirmation, creates boarding passes, and returns boarding pass metadata.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Check-in confirmed successfully",
        content = @Content(schema = @Schema(implementation = CheckInConfirmResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Booking or selection not found",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Booking not eligible for check-in",
        content = @Content)
  })
  CheckInConfirmResponse confirmCheckIn(@Valid @RequestBody CheckInConfirmRequest request) {
    CheckInConfirmView view =
        checkInConfirmService.confirmCheckIn(
            new CheckInConfirmCommand(
                request.pnrCode(),
                request.lastName(),
                request.segments().stream()
                    .map(
                        segment ->
                            new CheckInConfirmCommand.SegmentSelection(
                                segment.flightId(), segment.passengerIds()))
                    .toList()));
    checkInSuccessEmailService.sendSuccess(view);
    return toConfirmResponse(view);
  }

  private CheckInContinueResponse toResponse(CheckInContinueView view) {
    return new CheckInContinueResponse(
        new CheckInContinueResponse.BookingResponse(
            view.booking().bookingId(),
            view.booking().pnrCode(),
            view.booking().status(),
            view.booking().contactEmail(),
            view.booking().contactPhone(),
            view.booking().passengerCount(),
            view.booking().currency(),
            view.booking().lastName()),
        view.segments().stream()
            .map(
                segment ->
                    new CheckInContinueResponse.SegmentResponse(
                        segment.flightId(),
                        segment.flightNumber(),
                        segment.journeyType(),
                        segment.status(),
                        new CheckInContinueResponse.CheckInWindowResponse(
                            segment.checkInWindow().status(),
                            segment.checkInWindow().opensAt(),
                            segment.checkInWindow().closesAt(),
                            segment.checkInWindow().message()),
                        new CheckInContinueResponse.EndpointResponse(
                            segment.departure().airportCode(),
                            segment.departure().airportName(),
                            segment.departure().city(),
                            segment.departure().terminal(),
                            segment.departure().gate(),
                            segment.departure().time()),
                        new CheckInContinueResponse.EndpointResponse(
                            segment.arrival().airportCode(),
                            segment.arrival().airportName(),
                            segment.arrival().city(),
                            segment.arrival().terminal(),
                            segment.arrival().gate(),
                            segment.arrival().time()),
                        new CheckInContinueResponse.AirlineResponse(
                            segment.airline().code(),
                            segment.airline().name(),
                            segment.airline().logoUrl()),
                        new CheckInContinueResponse.FareResponse(
                            segment.fare().fareClass(), segment.fare().cabinClass()),
                        segment.passengers().stream()
                            .map(
                                passenger ->
                                    new CheckInContinueResponse.PassengerResponse(
                                        passenger.passengerId(),
                                        passenger.ticketId(),
                                        passenger.ticketNumber(),
                                        passenger.fullName(),
                                        passenger.dateOfBirth(),
                                        passenger.passengerType(),
                                        passenger.eligible(),
                                        passenger.checkInStatus(),
                                        new CheckInContinueResponse.SeatResponse(
                                            passenger.seat().assigned(),
                                            passenger.seat().seatId(),
                                            passenger.seat().seatNumber()),
                                        new CheckInContinueResponse.BaggageResponse(
                                            passenger.baggage().cabinKg(),
                                            passenger.baggage().checkedKg()),
                                        passenger.services().stream()
                                            .map(
                                                service ->
                                                    new CheckInContinueResponse.ServiceResponse(
                                                        service.code(),
                                                        service.name(),
                                                        service.quantity(),
                                                        service.status()))
                                            .toList(),
                                        passenger.warnings()))
                            .toList()))
            .toList(),
        new CheckInContinueResponse.SummaryResponse(
            view.summary().selectedSegments(),
            view.summary().selectedPassengers(),
            view.summary().readyPassengers(),
            view.summary().blockedPassengers(),
            view.summary().passengersWithoutSeat()),
        new CheckInContinueResponse.ActionsResponse(
            view.actions().allowConfirmCheckIn(),
            view.actions().allowSeatSelection(),
            view.actions().nextStep()));
  }

  private CheckInConfirmResponse toConfirmResponse(CheckInConfirmView view) {
    return new CheckInConfirmResponse(
        new CheckInConfirmResponse.BookingResponse(
            view.booking().bookingId(), view.booking().pnrCode(), view.booking().status()),
        view.segments().stream()
            .map(
                segment ->
                    new CheckInConfirmResponse.SegmentResponse(
                        segment.flightId(),
                        segment.flightNumber(),
                        segment.passengers().stream()
                            .map(
                                passenger ->
                                    new CheckInConfirmResponse.PassengerResponse(
                                        passenger.passengerId(),
                                        passenger.ticketId(),
                                        passenger.ticketNumber(),
                                        passenger.fullName(),
                                        passenger.seatNumber(),
                                        passenger.boardingPassId(),
                                        passenger.checkInStatus()))
                            .toList()))
            .toList(),
        view.boardingPasses().stream()
            .map(
                boardingPass ->
                    new CheckInConfirmResponse.BoardingPassResponse(
                        boardingPass.boardingPassId(),
                        boardingPass.ticketId(),
                        boardingPass.passengerId(),
                        boardingPass.flightId(),
                        boardingPass.qrCode(),
                        boardingPass.gate(),
                        boardingPass.boardingTime(),
                        boardingPass.sequenceNumber(),
                        boardingPass.seatLabel()))
            .toList(),
        new CheckInConfirmResponse.SummaryResponse(
            view.summary().confirmedPassengers(), view.summary().confirmedSegments()));
  }
}
