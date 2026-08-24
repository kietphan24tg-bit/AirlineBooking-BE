package com.example.moduleai.tool;

import com.example.moduleai.tool.core.AgentToolContext;
import com.example.moduleai.tool.core.AgentToolPolicy;
import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolExecutionGuard;
import com.example.moduleai.tool.core.ToolOperation;
import com.example.moduleai.tool.core.ToolResult;
import com.example.modulebooking.application.BookingLookupCommand;
import com.example.modulebooking.application.BookingLookupService;
import com.example.modulebooking.application.BookingLookupView;
import com.example.modulebooking.application.BookingManageOptionsService;
import com.example.modulebooking.application.BookingRevalidateService;
import com.example.modulebooking.application.BookingTicketsView;
import com.example.modulebooking.application.CreateBookingCommand;
import com.example.modulebooking.application.CreateBookingView;
import com.example.modulebooking.application.PassengerSummaryCommand;
import com.example.modulebooking.application.TicketIssuanceService;
import com.example.modulebooking.application.UserTripDetailService;
import com.example.modulebooking.application.UserTripHistoryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class BookingTools extends AbstractAgentTool {

  private final BookingLookupService bookingLookupService;
  private final BookingRevalidateService bookingRevalidateService;
  private final TicketIssuanceService ticketIssuanceService;
  private final UserTripHistoryService userTripHistoryService;
  private final UserTripDetailService userTripDetailService;
  private final BookingManageOptionsService bookingManageOptionsService;

  public BookingTools(
      ToolExecutionGuard guard,
      BookingLookupService bookingLookupService,
      BookingRevalidateService bookingRevalidateService,
      TicketIssuanceService ticketIssuanceService,
      UserTripHistoryService userTripHistoryService,
      UserTripDetailService userTripDetailService,
      BookingManageOptionsService bookingManageOptionsService) {
    super(guard);
    this.bookingLookupService = bookingLookupService;
    this.bookingRevalidateService = bookingRevalidateService;
    this.ticketIssuanceService = ticketIssuanceService;
    this.userTripHistoryService = userTripHistoryService;
    this.userTripDetailService = userTripDetailService;
    this.bookingManageOptionsService = bookingManageOptionsService;
  }

  @Tool(
      description =
          "Create a booking from a valid revalidation snapshot after the backend has trusted user confirmation.")
  @AgentToolPolicy(
      tool = "create_booking",
      operation = ToolOperation.WRITE,
      access = ToolAccess.AUTHENTICATED,
      confirmationRequired = true,
      idempotencyRequired = true)
  public ToolResult<CreateBookingOutput> create_booking(
      CreateBookingInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "create_booking",
        ToolOperation.WRITE,
        ToolAccess.PUBLIC,
        true,
        true,
        input,
        context,
        () ->
            CreateBookingOutput.from(
                bookingRevalidateService.createBooking(
                    context.userId(),
                    new CreateBookingCommand(
                        input.revalidateId(),
                        context.idempotencyKey(),
                        new PassengerSummaryCommand(
                            input.passengerSummary().adult(),
                            input.passengerSummary().child(),
                            input.passengerSummary().infant()),
                        input.contacts().stream()
                            .map(
                                contact ->
                                    new CreateBookingCommand.ContactCommand(
                                        contact.email(), contact.phone(), contact.primary()))
                            .toList(),
                        input.passengers().stream()
                            .map(
                                passenger ->
                                    new CreateBookingCommand.PassengerCommand(
                                        passenger.firstName(),
                                        passenger.lastName(),
                                        passenger.dateOfBirth(),
                                        passenger.gender(),
                                        passenger.nationality(),
                                        passenger.passengerType(),
                                        passenger.identifyType(),
                                        passenger.identifyNumber(),
                                        passenger.expiryDate()))
                            .toList()))));
  }

  @Tool(description = "Lookup a booking after the user provides its PNR and a passenger last name.")
  @AgentToolPolicy(
      tool = "lookup_booking",
      operation = ToolOperation.READ,
      access = ToolAccess.PUBLIC)
  public ToolResult<LookupBookingOutput> lookup_booking(
      LookupBookingInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "lookup_booking",
        ToolOperation.READ,
        ToolAccess.PUBLIC,
        false,
        false,
        input,
        context,
        () ->
            LookupBookingOutput.from(
                bookingLookupService.lookup(
                    new BookingLookupCommand(input.pnrCode(), input.lastName()))));
  }

  @Tool(description = "List trips owned by the authenticated user with status and date filters.")
  @AgentToolPolicy(
      tool = "list_my_trips",
      operation = ToolOperation.READ,
      access = ToolAccess.AUTHENTICATED)
  public ToolResult<UserTripHistoryService.UserTripHistoryResult> list_my_trips(
      ListMyTripsInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "list_my_trips",
        input,
        context,
        () ->
            userTripHistoryService.getTrips(
                context.userId(),
                new UserTripHistoryService.UserTripHistoryQuery(
                    input.status() == null
                        ? UserTripHistoryService.TripFilterStatus.UPCOMING
                        : input.status(),
                    input.query(),
                    input.fromDate(),
                    input.toDate(),
                    input.cursor(),
                    input.limit() == null ? 10 : input.limit(),
                    OffsetDateTime.now())));
  }

  @Tool(description = "Get one trip segment owned by the authenticated user.")
  @AgentToolPolicy(
      tool = "get_trip_detail",
      operation = ToolOperation.READ,
      access = ToolAccess.AUTHENTICATED)
  public ToolResult<UserTripDetailService.TripDetailView> get_trip_detail(
      TripDetailInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "get_trip_detail",
        input,
        context,
        () ->
            userTripDetailService.getTripDetail(
                context.userId(), input.bookingId(), input.flightId()));
  }

  @Tool(description = "Get authoritative manage-booking options for an owned booking.")
  @AgentToolPolicy(
      tool = "get_manage_options",
      operation = ToolOperation.READ,
      access = ToolAccess.BOOKING_VERIFIED)
  public ToolResult<BookingManageOptionsService.BookingManageOptionsView> get_manage_options(
      BookingIdInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "get_manage_options",
        input,
        context,
        () -> bookingManageOptionsService.getManageOptions(context.userId(), input.bookingId()));
  }

  @Tool(description = "Issue missing tickets and return the current ticket list for a booking.")
  @AgentToolPolicy(
      tool = "get_booking_tickets",
      operation = ToolOperation.READ,
      access = ToolAccess.BOOKING_VERIFIED)
  public ToolResult<BookingTicketsView> get_booking_tickets(
      BookingIdInput input, ToolContext toolContext) {
    AgentToolContext context = AgentToolContext.from(toolContext);
    return execute(
        "get_booking_tickets",
        ToolOperation.READ,
        ToolAccess.BOOKING_VERIFIED,
        false,
        false,
        input,
        context,
        () -> ticketIssuanceService.issueTicketsForBooking(context.userId(), input.bookingId()));
  }

  public record EmptyInput() {}

  public record ListMyTripsInput(
      UserTripHistoryService.TripFilterStatus status,
      @Size(max = 100) String query,
      LocalDate fromDate,
      LocalDate toDate,
      @Size(max = 500) String cursor,
      @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(50) Integer limit) {}

  public record TripDetailInput(
      @NotNull @Positive Long bookingId, @NotNull @Positive Long flightId) {}

  public record CreateBookingInput(
      @NotBlank String revalidateId,
      @NotNull PassengerSummaryInput passengerSummary,
      @NotNull List<ContactInput> contacts,
      @NotNull List<PassengerInput> passengers) {}

  public record PassengerSummaryInput(
      @NotNull @jakarta.validation.constraints.Min(0) Integer adult,
      @NotNull @jakarta.validation.constraints.Min(0) Integer child,
      @NotNull @jakarta.validation.constraints.Min(0) Integer infant) {}

  public record ContactInput(String email, String phone, Boolean primary) {}

  public record PassengerInput(
      @NotBlank String firstName,
      @NotBlank String lastName,
      @NotNull LocalDate dateOfBirth,
      @NotBlank String gender,
      String nationality,
      @NotBlank String passengerType,
      String identifyType,
      String identifyNumber,
      LocalDate expiryDate) {}

  public record LookupBookingInput(
      @NotBlank @Size(max = 20) String pnrCode, @NotBlank @Size(max = 100) String lastName) {}

  public record CreateBookingOutput(
      CreateBookingView.BookingView booking,
      CreateBookingView.FlightView flight,
      CreateBookingView.FareView fare,
      CreateBookingView.PricingView pricing,
      CreateBookingView.PromotionView promotion,
      List<CreateBookingView.ContactView> contacts,
      CreateBookingView.PassengerSummaryView passengerSummary,
      List<CreateBookingView.PassengerView> passengers,
      CreateBookingView.SeatReservationView seatReservation,
      CreateBookingView.NextActionView nextAction) {

    private static CreateBookingOutput from(CreateBookingView view) {
      return new CreateBookingOutput(
          view.booking(),
          view.flight(),
          view.fare(),
          view.pricing(),
          view.promotion(),
          view.contacts(),
          view.passengerSummary(),
          view.passengers(),
          view.seatReservation(),
          view.nextAction());
    }
  }

  public record LookupBookingOutput(
      BookingSummaryOutput booking,
      ContactOutput primaryContact,
      PriceBreakdownOutput price,
      List<ItineraryOutput> itineraries,
      List<PassengerOutput> passengers,
      ActionsOutput actions) {

    private static LookupBookingOutput from(BookingLookupView view) {
      return new LookupBookingOutput(
          BookingSummaryOutput.from(view.booking()),
          new ContactOutput(view.primaryContact().email(), view.primaryContact().phone()),
          PriceBreakdownOutput.from(view.paymentDetails().price()),
          view.itineraries().stream().map(ItineraryOutput::from).toList(),
          view.passengers().stream().map(PassengerOutput::from).toList(),
          ActionsOutput.from(view.actions()));
    }
  }

  public record BookingSummaryOutput(
      Long bookingId,
      String pnrCode,
      String status,
      String statusLabel,
      BigDecimal totalAmount,
      String currency,
      OffsetDateTime createdAt,
      OffsetDateTime expiresAt) {

    private static BookingSummaryOutput from(BookingLookupView.BookingSummary view) {
      return new BookingSummaryOutput(
          view.bookingId(),
          view.pnrCode(),
          view.status(),
          view.statusLabel(),
          view.totalAmount(),
          view.currency(),
          view.createdAt(),
          view.expiresAt());
    }
  }

  public record ContactOutput(String email, String phone) {}

  public record PriceBreakdownOutput(
      BigDecimal baseFare,
      BigDecimal discount,
      BigDecimal tax,
      BigDecimal serviceFee,
      BigDecimal seatFee,
      BigDecimal total,
      String currency) {

    private static PriceBreakdownOutput from(BookingLookupView.PriceBreakdown view) {
      return new PriceBreakdownOutput(
          view.baseFare(),
          view.discount(),
          view.tax(),
          view.serviceFee(),
          view.seatFee(),
          view.total(),
          view.currency());
    }
  }

  public record ItineraryOutput(
      Long flightId,
      String flightNumber,
      OffsetDateTime departureTime,
      OffsetDateTime arrivalTime,
      String departureAirportCode,
      String departureCity,
      String arrivalAirportCode,
      String arrivalCity,
      String flightStatus,
      String fareName,
      String cabinClass) {

    private static ItineraryOutput from(BookingLookupView.ItinerarySummary view) {
      return new ItineraryOutput(
          view.flightId(),
          view.flightNumber(),
          view.departureTime(),
          view.arrivalTime(),
          view.departureAirportCode(),
          view.departureCity(),
          view.arrivalAirportCode(),
          view.arrivalCity(),
          view.flightStatus(),
          view.fareName(),
          view.cabinClass());
    }
  }

  public record PassengerOutput(
      Long passengerId,
      String firstName,
      String lastName,
      String passengerType,
      String gender,
      LocalDate dateOfBirth,
      List<SeatAssignmentOutput> seats,
      List<ServiceSelectionOutput> services) {

    private static PassengerOutput from(BookingLookupView.PassengerSummary view) {
      return new PassengerOutput(
          view.passengerId(),
          view.firstName(),
          view.lastName(),
          view.passengerType(),
          view.gender(),
          view.dateOfBirth(),
          view.selections().seats().stream().map(SeatAssignmentOutput::from).toList(),
          view.selections().services().stream().map(ServiceSelectionOutput::from).toList());
    }
  }

  public record SeatAssignmentOutput(
      Long flightId,
      Long seatId,
      String seatNumber,
      String status,
      BigDecimal surchargeAmount,
      String currency) {

    private static SeatAssignmentOutput from(BookingLookupView.SeatAssignmentDetail view) {
      return new SeatAssignmentOutput(
          view.flightId(),
          view.seatId(),
          view.seatNumber(),
          view.status(),
          view.surchargeAmount(),
          view.currency());
    }
  }

  public record ServiceSelectionOutput(
      Long serviceId,
      String serviceType,
      String serviceName,
      Long optionId,
      String optionName,
      Integer quantity,
      BigDecimal price) {

    private static ServiceSelectionOutput from(BookingLookupView.ServiceSelectionDetail view) {
      return new ServiceSelectionOutput(
          view.serviceId(),
          view.serviceType(),
          view.serviceName(),
          view.optionId(),
          view.optionName(),
          view.quantity(),
          view.price());
    }
  }

  public record ActionsOutput(
      boolean canSelectSeat,
      boolean canBuyAncillaries,
      boolean canCheckIn,
      boolean canChangeFlight,
      boolean canCancel) {

    private static ActionsOutput from(BookingLookupView.Actions view) {
      return new ActionsOutput(
          view.canSelectSeat(),
          view.canBuyAncillaries(),
          view.canCheckIn(),
          view.canChangeFlight(),
          view.canCancel());
    }
  }

  public record BookingIdInput(@NotNull @Positive Long bookingId) {}
}
